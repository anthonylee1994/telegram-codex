package com.telegram.codex.conversation.application.gateway;

import com.telegram.codex.conversation.domain.session.Transcript;

public interface SessionCompactGateway {

    String compact(Transcript transcript);
}
