package com.telegram.codex.conversation.application

import com.telegram.codex.conversation.infrastructure.MediaGroupBufferRepository
import com.telegram.codex.integration.telegram.application.CompactResultSender
import com.telegram.codex.integration.telegram.application.InboundMessageProcessor
import com.telegram.codex.integration.telegram.domain.InboundMessage
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.Mockito.verify
import org.springframework.beans.factory.ObjectProvider
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class JobSchedulerServiceTest {
    private var service: JobSchedulerService? = null

    @AfterEach
    fun tearDown() {
        service?.shutdown()
    }

    @Test
    fun enqueueReplyGenerationRunsInsideWebProcess() {
        val replyGenerationService = Mockito.mock(ReplyGenerationService::class.java)
        val message = InboundMessage("3", emptyList(), null, 10, emptyList(), emptyList(), null, "hello", "5", 99)
        val latch = CountDownLatch(1)
        Mockito.doAnswer {
            latch.countDown()
            null
        }.`when`(replyGenerationService).handle(message)

        service = JobSchedulerService(
            Mockito.mock(MediaGroupBufferRepository::class.java),
            mockProcessorProvider(),
            replyGenerationService,
            Mockito.mock(SessionService::class.java),
            Mockito.mock(CompactResultSender::class.java),
        )

        service!!.enqueueReplyGeneration(message)

        assertTrue(latch.await(2, TimeUnit.SECONDS))
        verify(replyGenerationService).handle(message)
    }

    @Suppress("UNCHECKED_CAST")
    private fun mockProcessorProvider(): ObjectProvider<InboundMessageProcessor> {
        val provider = Mockito.mock(ObjectProvider::class.java) as ObjectProvider<InboundMessageProcessor>
        Mockito.`when`(provider.getObject()).thenReturn(Mockito.mock(InboundMessageProcessor::class.java))
        return provider
    }
}
