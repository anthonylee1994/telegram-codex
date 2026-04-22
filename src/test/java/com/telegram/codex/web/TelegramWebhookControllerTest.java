package com.telegram.codex.web;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.telegram.codex.config.AppProperties;
import com.telegram.codex.telegram.TelegramWebhookHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
    controllers = TelegramWebhookController.class,
    properties = "logging.level.com.telegram.codex.web.TelegramWebhookController=OFF"
)
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

        verify(webhookHandler).handle(anyMap());
    }

    @Test
    void returnsInternalServerErrorWhenHandlerFails() throws Exception {
        doThrow(new IllegalStateException("boom")).when(webhookHandler).handle(anyMap());

        mockMvc.perform(post("/telegram/webhook")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"update_id\":1}")
                .header("X-Telegram-Bot-Api-Secret-Token", "secret"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.ok").value(false));
    }
}
