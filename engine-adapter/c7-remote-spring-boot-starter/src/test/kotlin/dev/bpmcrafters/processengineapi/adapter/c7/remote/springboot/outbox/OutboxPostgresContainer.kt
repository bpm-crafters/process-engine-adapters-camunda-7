package dev.bpmcrafters.processengineapi.adapter.c7.remote.springboot.outbox

import org.testcontainers.containers.GenericContainer

class OutboxPostgresContainer : GenericContainer<OutboxPostgresContainer>("docker.io/library/postgres:16-alpine")
