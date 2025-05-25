package dev.bpmcrafters.processengineapi.adapter.c7.embedded.deploy

import dev.bpmcrafters.processengineapi.deploy.DeployBundleCommand
import dev.bpmcrafters.processengineapi.deploy.NamedResource
import org.assertj.core.api.Assertions.assertThat
import org.camunda.bpm.engine.RepositoryService
import org.camunda.bpm.engine.repository.Deployment
import org.camunda.bpm.engine.repository.DeploymentBuilder
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import java.util.*

class DeploymentApiImplTest {

  private val repositoryService: RepositoryService = mock()
  private val deploymentApiImpl = DeploymentApiImpl(repositoryService)

  @Test
  fun `empty tenant id was not set`() {
    val deploymentBuilder: DeploymentBuilder = mock()
    given(repositoryService.createDeployment()).willReturn(deploymentBuilder)
    val deployment: Deployment = mock()
    given(deploymentBuilder.deploy()).willReturn(deployment)
    given(deployment.tenantId).willReturn(null)
    given(deployment.deploymentTime).willReturn(Date())
    given(deployment.id).willReturn("myDeploymentId")
    val resource = NamedResource.fromClasspath("bpmn/simple-process.bpmn")

    val deploymentInformation = deploymentApiImpl.deploy(
      DeployBundleCommand(listOf(resource), "")
    ).get()

    verify(deploymentBuilder, never()).tenantId(any())
    assertThat(deploymentInformation.tenantId).isNull()
  }


}
