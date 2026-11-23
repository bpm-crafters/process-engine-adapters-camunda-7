package dev.bpmcrafters.processengineapi.adapter.c7.embedded.springboot

import dev.bpmcrafters.processengineapi.lifecycle.ProcessApplicationLifecycle
import dev.bpmcrafters.processengineapi.lifecycle.ProcessApplicationLifecycleContext
import dev.bpmcrafters.processengineapi.lifecycle.ProcessApplicationLifecycleParticipant

internal fun ProcessApplicationLifecycle.registerParticipant(
  phase: Int,
  action: (ProcessApplicationLifecycleContext) -> Unit
) {
  register(object : ProcessApplicationLifecycleParticipant {
    override fun getPhase(): Int = phase
    override fun onPhase(context: ProcessApplicationLifecycleContext) = action(context)
  })
}
