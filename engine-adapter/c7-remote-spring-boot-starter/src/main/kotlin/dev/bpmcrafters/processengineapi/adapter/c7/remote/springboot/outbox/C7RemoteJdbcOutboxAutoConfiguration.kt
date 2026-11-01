package dev.bpmcrafters.processengineapi.adapter.c7.remote.springboot.outbox

import dev.bpmcrafters.processengineapi.adapter.c7.remote.outbox.EngineCommandOutbox
import dev.bpmcrafters.processengineapi.adapter.c7.remote.springboot.C7RemoteAdapterEnabledCondition
import dev.bpmcrafters.processengineapi.adapter.c7.remote.springboot.C7RemoteAdapterProperties
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Conditional
import org.springframework.jdbc.core.simple.JdbcClient
import javax.sql.DataSource

/**
 * Default outbox, opted in by property. A custom [EngineCommandOutbox] bean takes precedence.
 */
@AutoConfiguration(
  afterName = [
    "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration",
    "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
  ],
  before = [C7RemoteOutboxAutoConfiguration::class]
)
@EnableConfigurationProperties(C7RemoteAdapterProperties::class)
@Conditional(C7RemoteAdapterEnabledCondition::class)
@ConditionalOnClass(JdbcClient::class)
@ConditionalOnProperty(prefix = "${C7RemoteAdapterProperties.DEFAULT_PREFIX}.outbox", name = ["enabled"], havingValue = "true")
class C7RemoteJdbcOutboxAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean(EngineCommandOutbox::class)
  fun jdbcEngineCommandOutbox(dataSource: DataSource, properties: C7RemoteAdapterProperties): JdbcEngineCommandOutbox =
    JdbcEngineCommandOutbox(dataSource, properties.outbox.deserializationFilter)
}
