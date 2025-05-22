package dev.bpmcrafters.processengineapi.adapter.c7.remote.springboot.feign

/**
 * Specific exception wrapping all errors thrown by the engine.
 */
class C7RemoteException(message: String, cause: Throwable?)
  : RuntimeException(message, cause)
