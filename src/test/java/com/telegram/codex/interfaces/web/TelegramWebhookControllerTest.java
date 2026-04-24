package com.telegram.codex.interfaces.web;

import com.telegram.codex.bootstrap.TelegramCodexApplication;
import com.telegram.codex.integration.telegram.application.webhook.TelegramWebhookHandler;
import com.telegram.codex.integration.telegram.domain.webhook.TelegramUpdate;
import com.telegram.codex.shared.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
    controllers = TelegramWebhookController.class,
    properties = "logging.level.com.telegram.codex.web.TelegramWebhookController=OFF"
)
@ContextConfiguration(classes = {TelegramCodexApplication.class, TelegramWebhookControllerTest.TestConfig.class})
@Import(TelegramWebhookControllerTest.TestConfig.class)
class TelegramWebhookControllerTest {

    @TestConfiguration
    @EnableConfigurationProperties(AppProperties.class)
    static class TestConfig {
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TelegramWebhookHandler webhookHandler;

    @Test
    void rejectsInvalidSecret() throws Exception {
        mockMvc.perform(post("/telegram/webhook")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"update_id\":1}")
                .header("X-Telegram-Bot-Api-Secret-Token", "bad"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.ok").value(false));
    }

    @Test
    void acceptsValidSecret() throws Exception {
        mockMvc.perform(post("/telegram/webhook")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"update_id\":1}")
                .header("X-Telegram-Bot-Api-Secret-Token", "secret"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ok").value(true));

        verify(webhookHandler).handle(any(TelegramUpdate.class));
    }

    @Test
    void returnsInternalServerErrorWhenHandlerFails() throws Exception {
        doThrow(new IllegalStateException("boom")).when(webhookHandler).handle(any(TelegramUpdate.class));

        mockMvc.perform(post("/telegram/webhook")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"update_id\":1}")
                .header("X-Telegram-Bot-Api-Secret-Token", "secret"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.ok").value(false));
    }
}
