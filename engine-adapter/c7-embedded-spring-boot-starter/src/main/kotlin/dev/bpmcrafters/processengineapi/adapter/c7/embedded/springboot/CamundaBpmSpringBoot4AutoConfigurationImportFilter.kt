package dev.bpmcrafters.processengineapi.adapter.c7.embedded.springboot

import org.springframework.boot.autoconfigure.AutoConfigurationImportFilter
import org.springframework.boot.autoconfigure.AutoConfigurationMetadata
import org.springframework.util.ClassUtils

/**
 * Prevents Spring Boot 4 from importing Camunda's Boot-3-targeted auto-configuration.
 *
 * The old Hibernate JPA auto-configuration class is used as the runtime marker: if it exists we are
 * on the Boot 3 classpath and leave Camunda untouched; if it is missing we filter Camunda's original
 * auto-configuration so the local Boot 4 replacement can take over.
 */
class CamundaBpmSpringBoot4AutoConfigurationImportFilter : AutoConfigurationImportFilter {

  override fun match(
    autoConfigurationClasses: Array<String?>,
    autoConfigurationMetadata: AutoConfigurationMetadata
  ): BooleanArray {
    val camundaBoot3AutoConfigurationCanLoad = ClassUtils.isPresent(
      BOOT3_HIBERNATE_JPA_AUTO_CONFIGURATION,
      javaClass.classLoader
    )

    return autoConfigurationClasses.map { autoConfigurationClass ->
      autoConfigurationClass != CAMUNDA_BPM_AUTO_CONFIGURATION || camundaBoot3AutoConfigurationCanLoad
    }.toBooleanArray()
  }

  companion object {
    private const val CAMUNDA_BPM_AUTO_CONFIGURATION =
      "org.camunda.bpm.spring.boot.starter.CamundaBpmAutoConfiguration"
    private const val BOOT3_HIBERNATE_JPA_AUTO_CONFIGURATION =
      "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration"
  }
}
