package dev.bpmcrafters.processengineapi.adapter.c7.remote.correlation

import dev.bpmcrafters.processengineapi.CommonRestrictions
import org.camunda.community.rest.client.model.CorrelationMessageDto

fun CorrelationMessageDto.applyRestrictions(restrictions: Map<String, String>) = this.apply {
  val tenantId = restrictions[CommonRestrictions.TENANT_ID]
  if (!tenantId.isNullOrBlank()) {
    this.tenantId(tenantId)
  } else {
    val withoutTenantId = restrictions[CommonRestrictions.WITHOUT_TENANT_ID]
    if (!withoutTenantId.isNullOrBlank()) {
      this.withoutTenantId(true)
    }
  }
}
