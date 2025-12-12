package dev.bpmcrafters.processengineapi.test

import com.tngtech.jgiven.annotation.ProvidedScenarioState
import com.tngtech.jgiven.integration.spring.junit5.SpringScenarioTest

abstract class JGivenSpringBaseIntegrationTest : SpringScenarioTest<BaseGivenWhenStage, BaseGivenWhenStage, BaseThenStage>() {
  @ProvidedScenarioState
  open lateinit var processTestHelper: ProcessTestHelper
}
