package dev.bpmcrafters.processengineapi.adapter.c7.embedded.springboot

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PostConstruct
import org.camunda.bpm.engine.spring.SpringProcessEngineServicesConfiguration
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl
import org.camunda.bpm.engine.impl.interceptor.CommandExecutor
import org.camunda.bpm.engine.spring.ProcessEngineFactoryBean
import org.camunda.bpm.spring.boot.starter.CamundaBpmActuatorConfiguration
import org.camunda.bpm.spring.boot.starter.CamundaBpmConfiguration
import org.camunda.bpm.spring.boot.starter.CamundaBpmPluginConfiguration
import org.camunda.bpm.spring.boot.starter.CamundaBpmTelemetryConfiguration
import org.camunda.bpm.spring.boot.starter.event.ProcessApplicationEventPublisher
import org.camunda.bpm.spring.boot.starter.property.CamundaBpmProperties
import org.camunda.bpm.spring.boot.starter.property.ManagementProperties
import org.camunda.bpm.spring.boot.starter.util.CamundaBpmVersion
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.AutoConfigureAfter
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary

private val logger = KotlinLogging.logger {}

/**
 * Recreates the relevant parts of Camunda's own `CamundaBpmAutoConfiguration` when running on
 * Spring Boot 4.
 *
 * Camunda 7.24 still references Boot 3's Hibernate JPA auto-configuration package. The companion
 * import filter removes that original Camunda auto-configuration on Boot 4, so this replacement must
 * stay independent from the BPM Crafters adapter enabled flag: applications may disable the adapter
 * and still expect an embedded Camunda engine to be configured.
 */
@AutoConfiguration
@AutoConfigureAfter(name = ["org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration"])
@ConditionalOnMissingClass("org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration")
@ConditionalOnClass(name = ["org.camunda.bpm.spring.boot.starter.CamundaBpmConfiguration"])
@ConditionalOnProperty(prefix = "camunda.bpm", name = ["enabled"], matchIfMissing = true)
@EnableConfigurationProperties(value = [CamundaBpmProperties::class, ManagementProperties::class])
@Import(
  value = [
    CamundaBpmConfiguration::class,
    CamundaBpmActuatorConfiguration::class,
    CamundaBpmPluginConfiguration::class,
    CamundaBpmTelemetryConfiguration::class,
    SpringProcessEngineServicesConfiguration::class
  ]
)
class C7EmbeddedCamundaBpmSpringBoot4AutoConfiguration {

  @PostConstruct
  fun report() {
    logger.debug { "PROCESS-ENGINE-C7-EMBEDDED-204: Spring Boot 4 Camunda compatibility configuration applied." }
  }

  @Bean
  fun camundaBpmVersion(): CamundaBpmVersion = CamundaBpmVersion()

  @Bean
  fun processApplicationEventPublisher(
    publisher: ApplicationEventPublisher
  ): ProcessApplicationEventPublisher = ProcessApplicationEventPublisher(publisher)

  @Bean
  fun processEngineFactoryBean(
    processEngineConfigurationImpl: ProcessEngineConfigurationImpl
  ): ProcessEngineFactoryBean {
    val factoryBean = ProcessEngineFactoryBean()
    factoryBean.processEngineConfiguration = processEngineConfigurationImpl
    return factoryBean
  }

  @Bean
  @Primary
  fun commandExecutorTxRequired(
    processEngineConfigurationImpl: ProcessEngineConfigurationImpl
  ): CommandExecutor = processEngineConfigurationImpl.commandExecutorTxRequired

  @Bean
  fun commandExecutorTxRequiresNew(
    processEngineConfigurationImpl: ProcessEngineConfigurationImpl
  ): CommandExecutor = processEngineConfigurationImpl.commandExecutorTxRequiresNew

  @Bean
  fun commandExecutorSchemaOperations(
    processEngineConfigurationImpl: ProcessEngineConfigurationImpl
  ): CommandExecutor = processEngineConfigurationImpl.commandExecutorSchemaOperations
}
