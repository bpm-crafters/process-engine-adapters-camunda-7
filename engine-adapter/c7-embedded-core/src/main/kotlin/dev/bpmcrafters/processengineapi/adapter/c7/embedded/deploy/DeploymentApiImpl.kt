package dev.bpmcrafters.processengineapi.adapter.c7.embedded.deploy

import dev.bpmcrafters.processengineapi.MetaInfo
import dev.bpmcrafters.processengineapi.MetaInfoAware
import dev.bpmcrafters.processengineapi.deploy.DeployBundleCommand
import dev.bpmcrafters.processengineapi.deploy.DeploymentApi
import dev.bpmcrafters.processengineapi.deploy.DeploymentInformation
import io.github.oshai.kotlinlogging.KotlinLogging
import org.camunda.bpm.engine.RepositoryService
import org.camunda.bpm.engine.repository.Deployment
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future

private val logger = KotlinLogging.logger {}

/**
 * Implementation for deployment API using repository service.
 */
class DeploymentApiImpl(
  private val repositoryService: RepositoryService
) : DeploymentApi {

  override fun deploy(cmd: DeployBundleCommand): Future<DeploymentInformation> {
    require(cmd.resources.isNotEmpty()) { "Resources must not be empty, at least one resource must be provided." }
    logger.debug { "PROCESS-ENGINE-C7-EMBEDDED-003: executing a bundle deployment with ${cmd.resources.size} resources." }
    return CompletableFuture.supplyAsync {
      repositoryService
        .createDeployment()
        .apply {
          cmd.resources.forEach { resource -> this.addInputStream(resource.name, resource.resourceStream) }
        }
        .apply {
          if (!cmd.tenantId.isNullOrBlank()) {
            this.tenantId(cmd.tenantId)
          }
        }
        .deploy()
        .toDeploymentInformation()
    }
  }

  private fun Deployment.toDeploymentInformation() = DeploymentInformation(
    deploymentKey = this.id,
    tenantId = this.tenantId,
    deploymentTime = this.deploymentTime.toInstant()
  )

  override fun meta(instance: MetaInfoAware): MetaInfo {
    TODO("Not yet implemented")
  }

}
