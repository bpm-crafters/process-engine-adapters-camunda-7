package dev.bpmcrafters.processengineapi.adapter.c7.embedded.correlation

import dev.bpmcrafters.processengineapi.CommonRestrictions
import org.camunda.bpm.engine.runtime.MessageCorrelationBuilder

/**
 * Checks that the restrictions on tenant usage are not misconfigured.
 * @param restrictions restrictions to check.
 * @return message correlation builder.
 * @throws IllegalArgumentException on misconfiguration.
 */
fun MessageCorrelationBuilder.applyTenantRestrictions(restrictions: Map<String, String>) = this.apply {
  restrictions
    .forEach { (key, value) ->
      when (key) {
        CommonRestrictions.TENANT_ID -> this.tenantId(value).apply {
          require(!restrictions.containsKey(CommonRestrictions.WITHOUT_TENANT_ID)) { "Illegal restriction combination. ${CommonRestrictions.TENANT_ID} " +
            "and ${CommonRestrictions.WITHOUT_TENANT_ID} can't be provided in the same time because they are mutually exclusive." }
        }
        CommonRestrictions.WITHOUT_TENANT_ID -> this.withoutTenantId().apply {
          require(!restrictions.containsKey(CommonRestrictions.TENANT_ID)) { "Illegal restriction combination. ${CommonRestrictions.TENANT_ID} " +
            "and ${CommonRestrictions.WITHOUT_TENANT_ID} can't be provided in the same time because they are mutually exclusive." }
        }
      }
    }
}
