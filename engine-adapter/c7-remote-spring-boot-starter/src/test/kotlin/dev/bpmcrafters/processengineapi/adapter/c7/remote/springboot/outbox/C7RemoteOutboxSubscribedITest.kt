package dev.bpmcrafters.processengineapi.adapter.c7.remote.springboot.outbox

import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("itest-subscribed")
class C7RemoteOutboxSubscribedITest : C7RemoteOutboxITest()
