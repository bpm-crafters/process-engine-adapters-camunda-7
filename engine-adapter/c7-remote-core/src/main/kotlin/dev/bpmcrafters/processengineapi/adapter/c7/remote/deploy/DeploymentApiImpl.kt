package dev.bpmcrafters.processengineapi.adapter.c7.remote.deploy

import dev.bpmcrafters.processengineapi.MetaInfo
import dev.bpmcrafters.processengineapi.MetaInfoAware
import dev.bpmcrafters.processengineapi.deploy.DeployBundleCommand
import dev.bpmcrafters.processengineapi.deploy.DeploymentApi
import dev.bpmcrafters.processengineapi.deploy.DeploymentInformation
import io.github.oshai.kotlinlogging.KotlinLogging
import org.camunda.community.rest.client.api.DeploymentApiClient
import org.camunda.community.rest.client.model.DeploymentWithDefinitionsDto
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future

private val logger = KotlinLogging.logger {}

class DeploymentApiImpl(
  private val deploymentApiClient: DeploymentApiClient
) : DeploymentApi {

  override fun deploy(cmd: DeployBundleCommand): Future<DeploymentInformation> {
    require(cmd.resources.isNotEmpty()) { "Resources must not be empty, at least one resource must be provided." }
    logger.debug { "PROCESS-ENGINE-C7-REMOTE-003: executing a bundle deployment with ${cmd.resources.size} resources." }
    return CompletableFuture.supplyAsync {

      val tenantId = if (cmd.tenantId.isNullOrBlank()) {
        null
      } else {
        cmd.tenantId
      }
      val deployment = deploymentApiClient.createDeployment(
        tenantId, // tenant-id
        null, // deployment-source
        false, // deploy-changed-only
        true, // enable-duplicate-filtering
        "ProcessEngineApiRemote", // deployment name
        null, // deployment-activation-time
        cmd.resources.map { resource ->
          NamedResourceMultipartFile(resource)
        }.toTypedArray()
      )

      requireNotNull(deployment.body) { "Could not create deployment, status of deployment status was '${deployment.statusCode}'" }
        .toDeploymentInformation()
    }
  }

  override fun meta(instance: MetaInfoAware): MetaInfo {
    TODO("Not yet implemented")
  }

  private fun DeploymentWithDefinitionsDto.toDeploymentInformation() = DeploymentInformation(
    deploymentKey = this.id,
    deploymentTime = this.deploymentTime.toInstant(),
    tenantId = this.tenantId
  )
}
