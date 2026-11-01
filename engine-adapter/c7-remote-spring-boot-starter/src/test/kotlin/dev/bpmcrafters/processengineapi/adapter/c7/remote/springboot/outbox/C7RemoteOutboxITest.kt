package dev.bpmcrafters.processengineapi.adapter.c7.remote.springboot.outbox

import dev.bpmcrafters.processengineapi.adapter.c7.remote.correlation.CorrelationApiImpl
import dev.bpmcrafters.processengineapi.adapter.c7.remote.springboot.AbstractC7RemoteApiITestBase
import dev.bpmcrafters.processengineapi.correlation.Correlation
import dev.bpmcrafters.processengineapi.correlation.CorrelateMessageCmd
import dev.bpmcrafters.processengineapi.correlation.CorrelationApi
import dev.bpmcrafters.processengineapi.correlation.SendSignalCmd
import dev.bpmcrafters.processengineapi.correlation.SignalApi
import dev.bpmcrafters.processengineapi.deploy.DeployBundleCommand
import dev.bpmcrafters.processengineapi.deploy.NamedResource
import dev.bpmcrafters.processengineapi.task.CompleteTaskCmd
import dev.bpmcrafters.processengineapi.task.ServiceTaskCompletionApi
import dev.bpmcrafters.processengineapi.task.SubscribeForTaskCmd
import dev.bpmcrafters.processengineapi.task.TaskSubscriptionApi
import dev.bpmcrafters.processengineapi.task.TaskType
import dev.bpmcrafters.processengineapi.task.UserTaskCompletionApi
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.junit.jupiter.Container
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.UUID
import javax.sql.DataSource

class OutboxPostgresContainer : GenericContainer<OutboxPostgresContainer>("docker.io/library/postgres:16-alpine")

@ActiveProfiles("itest")
@Import(C7RemoteOutboxITest.Config::class)
class C7RemoteOutboxITest : AbstractC7RemoteApiITestBase() {

  companion object {
    private const val PROCESS = "outbox-process"

    @JvmStatic
    @Container
    val postgres: OutboxPostgresContainer = OutboxPostgresContainer()
      .withEnv("POSTGRES_PASSWORD", "outbox")
      .withExposedPorts(5432)
      .waitingFor(Wait.forLogMessage(".*ready to accept connections.*\\n", 2))

    @JvmStatic
    @DynamicPropertySource
    fun outbox(registry: DynamicPropertyRegistry) {
      registry.add("dev.bpm-crafters.process-api.adapter.c7remote.outbox.enabled") { true }
      registry.add("dev.bpm-crafters.process-api.adapter.c7remote.outbox.lease-duration") { "5s" }
    }
  }

  @TestConfiguration
  class Config {
    @Bean
    fun dataSource(): DataSource = DriverManagerDataSource(
      "jdbc:postgresql://localhost:${postgres.firstMappedPort}/postgres", "postgres", "outbox"
    ).also {
      JdbcClient.create(it).sql(
        """
        CREATE TABLE c7remote_engine_outbox (
          id VARCHAR(36) PRIMARY KEY, type VARCHAR(32) NOT NULL, task_id VARCHAR(64), payload BYTEA NOT NULL,
          status VARCHAR(16) NOT NULL, attempts INT NOT NULL, rejections INT NOT NULL, next_attempt_at TIMESTAMP NOT NULL,
          lease_until TIMESTAMP, last_error VARCHAR(4000))
        """
      ).update()
    }

    @Bean
    fun transactionManager(dataSource: DataSource): PlatformTransactionManager = DataSourceTransactionManager(dataSource)
  }

  @Autowired lateinit var dataSource: DataSource
  @Autowired lateinit var transactionManager: PlatformTransactionManager
  @Autowired lateinit var subscriptions: TaskSubscriptionApi
  @Autowired lateinit var serviceTasks: ServiceTaskCompletionApi
  @Autowired lateinit var userTasks: UserTaskCompletionApi
  @Autowired lateinit var correlation: CorrelationApi
  @Autowired lateinit var signals: SignalApi

  @Test
  fun `delivers service task, user task, message and signal after commit`() {
    prepare()
    val key = UUID.randomUUID().toString()
    val instance = startProcess(key)
    val serviceTaskId = lockedServiceTask(instance)

    inTransaction { serviceTasks.completeTask(CompleteTaskCmd(serviceTaskId) { mapOf("done" to true) }).get() }
    awaitElement(instance, "outbox-user")
    val userTaskId = id(engine("task?processInstanceId=$instance"))

    inTransaction { userTasks.completeTask(CompleteTaskCmd(userTaskId) { emptyMap() }).get() }
    awaitElement(instance, "outbox-message")

    rollingBack { sendMessage(key) }
    assertThat(outboxRows()).isZero()
    Thread.sleep(2_000)
    assertThat(myProcessTestHelper.getActiveElements(instance)).contains("outbox-message")

    inTransaction { sendMessage(key) }
    awaitElement(instance, "outbox-signal")

    inTransaction { signals.sendSignal(SendSignalCmd("outboxSignal", { emptyMap() }, mapOf())).get() }
    await("process finished") { engine("process-instance/count?processInstanceIds=$instance").contains("\"count\":0") }
    await("outbox drained") { outboxRows() == 0 }
  }

  @Test
  fun `keeps command during engine outage and delivers afterwards without burning retries`() {
    prepare()
    val instance = startProcess(UUID.randomUUID().toString())
    val serviceTaskId = lockedServiceTask(instance)
    val docker = camundaContainer.dockerClient
    val engineId = camundaContainer.containerId

    docker.pauseContainerCmd(engineId).exec()
    try {
      inTransaction { serviceTasks.completeTask(CompleteTaskCmd(serviceTaskId) { emptyMap() }).get() }
      assertThat(outboxRows()).isEqualTo(1)
      Thread.sleep(8_000)
      assertThat(outboxRows()).isEqualTo(1)
    } finally {
      docker.unpauseContainerCmd(engineId).exec()
    }

    awaitElement(instance, "outbox-user")
    await("outbox drained") { outboxRows() == 0 }
    assertThat(engine("incident/count?processInstanceId=$instance")).contains("\"count\":0")
  }

  private fun prepare() {
    deploymentApi.deploy(DeployBundleCommand(listOf(NamedResource.fromClasspath("bpmn/$PROCESS.bpmn")))).get()
    subscribe(TaskType.EXTERNAL, "outbox-service")
    subscribe(TaskType.USER, "outbox-user")
  }

  private fun subscribe(type: TaskType, key: String) {
    subscriptions.subscribeForTask(
      SubscribeForTaskCmd(
        restrictions = emptyMap(),
        taskType = type,
        taskDescriptionKey = key,
        action = { _, _ -> },
        termination = {},
      )
    ).get()
  }

  private fun lockedServiceTask(instance: String): String {
    await("service task locked") { engine("external-task?processInstanceId=$instance").contains("\"workerId\":\"") }
    return id(engine("external-task?processInstanceId=$instance"))
  }

  private fun id(json: String) = Regex("\"id\":\"([^\"]+)\"").find(json)!!.groupValues[1]

  private fun sendMessage(key: String) {
    correlation.correlateMessage(
      CorrelateMessageCmd(
        "outboxMessage",
        { emptyMap() },
        { Correlation.withKey(key).withVariable("businessKey") },
        mapOf(CorrelationApiImpl.Restrictions.USE_GLOBAL_CORRELATION_KEY to "true"),
      )
    ).get()
  }

  private fun inTransaction(block: () -> Unit) = TransactionTemplate(transactionManager).executeWithoutResult { block() }

  private fun rollingBack(block: () -> Unit) = TransactionTemplate(transactionManager).executeWithoutResult {
    block()
    it.setRollbackOnly()
  }

  private fun outboxRows(): Int =
    JdbcClient.create(dataSource).sql("SELECT COUNT(*) FROM c7remote_engine_outbox").query(Int::class.java).single()

  private fun startProcess(key: String): String {
    val body = """{"variables":{"businessKey":{"value":"$key","type":"String"}}}"""
    val response = http(HttpRequest.newBuilder(uri("process-definition/key/$PROCESS/start"))
      .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build())
    return Regex("\"id\":\"([^\"]+)\"").findAll(response).last().groupValues[1]
  }

  private fun engine(path: String): String = http(HttpRequest.newBuilder(uri(path)).GET().build())

  private fun uri(path: String) = URI.create("http://localhost:${camundaContainer.firstMappedPort}/engine-rest/$path")

  private fun http(request: HttpRequest): String =
    HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString()).body()

  private fun awaitElement(instance: String, element: String) =
    await("$element active") { myProcessTestHelper.getActiveElements(instance).contains(element) }

  private fun await(what: String, condition: () -> Boolean) {
    val deadline = System.nanoTime() + Duration.ofSeconds(120).toNanos()
    while (System.nanoTime() < deadline) {
      if (runCatching(condition).getOrDefault(false)) return
      Thread.sleep(250)
    }
    throw AssertionError("Timed out waiting for $what, outbox: ${JdbcClient.create(dataSource).sql("SELECT status, rejections, attempts, last_error FROM c7remote_engine_outbox").query().listOfRows()}")
  }
}
