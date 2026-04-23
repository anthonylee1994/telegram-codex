package com.telegram.codex.conversation.application.port.out;

import com.telegram.codex.conversation.domain.session.Transcript;

public interface SessionCompactPort {

    String compact(Transcript transcript);
}
