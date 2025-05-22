package dev.bpmcrafters.processengineapi.adapter.c7.remote.deploy

import dev.bpmcrafters.processengineapi.MetaInfo
import dev.bpmcrafters.processengineapi.MetaInfoAware
import dev.bpmcrafters.processengineapi.deploy.DeployBundleCommand
import dev.bpmcrafters.processengineapi.deploy.DeploymentApi
import dev.bpmcrafters.processengineapi.deploy.DeploymentInformation
import io.github.oshai.kotlinlogging.KotlinLogging
import org.camunda.community.rest.client.api.DeploymentApiClient
import org.camunda.community.rest.client.model.DeploymentWithDefinitionsDto
import org.springframework.web.multipart.MultipartFile
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
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

      val resources: List<MultipartFile> = cmd.resources.map { resource ->
        object : MultipartFile {
          override fun getInputStream(): InputStream = resource.resourceStream
          override fun getName(): String = resource.name
          override fun getOriginalFilename(): String = resource.name
          override fun getContentType(): String? = null
          override fun isEmpty(): Boolean = resource.resourceStream.available() != 0
          override fun getSize(): Long = resource.resourceStream.available().toLong()
          override fun getBytes(): ByteArray = resource.resourceStream.readBytes()
          override fun transferTo(file: File) {
            var outputStream: OutputStream? = null
            try {
              outputStream = FileOutputStream(file)
              outputStream.write(bytes)
            } finally {
              outputStream?.close()
            }
          }
        }
      }

      val deployment = deploymentApiClient.createDeployment(
        if (cmd.tenantId.isNullOrBlank()) { null } else { cmd.tenantId },
        null,
        false,
        true,
        "ProcessEngineApiRemote",
        null,
        resources.toTypedArray()
      )

      requireNotNull(deployment.body) { "Could not create deployment, status of deployment status was '${deployment.statusCode}'" }.toDeploymentInformation()
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
