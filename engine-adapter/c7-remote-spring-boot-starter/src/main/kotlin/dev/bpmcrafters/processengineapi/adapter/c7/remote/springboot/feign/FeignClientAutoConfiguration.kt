package dev.bpmcrafters.processengineapi.adapter.c7.remote.springboot.feign

import dev.bpmcrafters.processengineapi.adapter.c7.remote.springboot.C7RemoteAdapterEnabledCondition
import feign.codec.ErrorDecoder
import org.camunda.community.rest.client.EnableCamundaFeignClients
import org.camunda.community.rest.exception.CamundaHttpFeignErrorDecoder
import org.camunda.community.rest.exception.ClientExceptionFactory
import org.camunda.community.rest.variables.ValueMapperConfiguration
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Conditional

@AutoConfiguration
@EnableCamundaFeignClients
@Conditional(C7RemoteAdapterEnabledCondition::class)
@ImportAutoConfiguration(ValueMapperConfiguration::class)
class FeignClientAutoConfiguration {

  @Bean
  fun c7ErrorDecoder(): ErrorDecoder {
    return CamundaHttpFeignErrorDecoder(
      httpCodes = listOf(400, 500), // current default
      defaultDecoder = ErrorDecoder.Default(),
      wrapExceptions = true, // wrap exception
      targetExceptionType = C7RemoteException::class.java,
      exceptionFactory = object : ClientExceptionFactory<C7RemoteException> {
        override fun create(message: String, cause: Throwable?) = C7RemoteException(message, cause)
      }
    )
  }

}
