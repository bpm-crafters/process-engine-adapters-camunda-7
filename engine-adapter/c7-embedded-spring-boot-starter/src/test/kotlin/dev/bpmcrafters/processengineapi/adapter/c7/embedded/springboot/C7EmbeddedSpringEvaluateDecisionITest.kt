package dev.bpmcrafters.processengineapi.adapter.c7.embedded.springboot

import com.fasterxml.jackson.annotation.JsonProperty
import dev.bpmcrafters.processengineapi.test.ProcessTestHelper
import io.toolisticon.testing.jgiven.AND
import io.toolisticon.testing.jgiven.GIVEN
import io.toolisticon.testing.jgiven.THEN
import io.toolisticon.testing.jgiven.WHEN
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class C7EmbeddedSpringEvaluateDecisionITest(
  @Autowired processTestHelperImpl: ProcessTestHelper
) : AbstractC7EmbeddedSpringApiITest(processTestHelperImpl) {

  @Test
  fun `should fail when decision evaluation returned failure in the response`() {
    GIVEN
      .`process helper`(this.processTestHelper)
    WHEN
      .`evaluate decision by ref key with payload`("FailedDecision", mapOf("id" to 99, "amount" to 5000))
    THEN
      .`we should have thrown`(RuntimeException::class)
  }

  @Test
  fun `should fail if evaluation command references on non-existent decision`() {
    GIVEN
      .`process helper`(this.processTestHelper)
    WHEN
      .`evaluate decision by ref key with payload`("NonExistentDecision", emptyMap())
    THEN
      .`we should have thrown`(RuntimeException::class)
  }

  @Test
  fun `should be casted successfully evaluation decision returning single hit single-output result`() {
    GIVEN
      .`process helper`(this.processTestHelper)
    WHEN
      .`evaluate decision by ref key with payload`("SingleSOutputDecision", mapOf("id" to 99, "amount" to 5000))
    THEN
      .`evaluation result interpreted as ` { it.asSingle().asType(Integer::class.java) }
      .AND
      .`interpreted result is`(10)
  }

  @Test
  fun `should be casted successfully to nullable if single hit decision gives no result`() {
    GIVEN
      .`process helper`(this.processTestHelper)
    WHEN
      .`evaluate decision by ref key with payload`("SingleSOutputDecision", mapOf("id" to 1000, "amount" to 5000))
    THEN
      .`evaluation result interpreted as ` { it.asSingle().asType(Integer::class.java) }
      .AND
      .`interpreted result is`(null)
  }

  data class Offer(
    @field:JsonProperty("score")
    val score: Double,
    @field:JsonProperty("message")
    val message: String
  )

  @Test
  fun `should be casted successfully to nullable if multi hit decision gives no result`() {
    GIVEN
      .`process helper`(this.processTestHelper)
    WHEN
      .`evaluate decision by ref key with payload`("CollectMOutputDecision", mapOf("id" to 1000, "amount" to 5000))
    THEN
      .`evaluation result interpreted as ` { it.asList().map { value -> value.asType(Offer::class.java) } }
      .AND
      .`interpreted result is`(emptyList<Offer>())
  }

  @Test
  fun `should fail if expected single-hit decision gives one or multi-hit results`() {
    GIVEN
      .`process helper`(this.processTestHelper)
    WHEN
      .`evaluate decision by ref key with payload`("CollectMOutputDecision", mapOf("id" to 99, "amount" to 5000))
    THEN
      .`evaluation result interpreted as ` { it.asSingle().asType(Offer::class.java) }
      .AND
      .`should interpretation fail`(Exception::class)
  }

  @Disabled("fails on Camunda 7, works on Camunda 8")
  @Test
  fun `should fail if expected multi-hit decision gives single-hit result`() {
    GIVEN
      .`process helper`(this.processTestHelper)
    WHEN
      .`evaluate decision by ref key with payload`("SingleSOutputDecision", mapOf("id" to 99, "amount" to 5000))
    THEN
      .`evaluation result interpreted as ` { it.asSingle().asType(Offer::class.java) }
      .AND
      .`should interpretation fail`(Exception::class)
  }

  @Test
  fun `should be casted successfully to list if multi hit decision gives one or more results`() {
    GIVEN
      .`process helper`(this.processTestHelper)
    WHEN
      .`evaluate decision by ref key with payload`("CollectMOutputDecision", mapOf("id" to 99, "amount" to 5000))
    THEN
      .`evaluation result interpreted as ` {
        it.asList().map { value -> value.asType(Offer::class.java) }
      }
      .AND
      .`interpreted result is`(listOf(Offer(23.5, "no-PIN"), Offer(43.0, "OK")))
  }

}
