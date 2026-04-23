package com.telegram.codex.conversation.application.port.out;

import java.util.List;

public interface SuggestedRepliesPort {

    List<String> parseSuggestedReplies(String rawSuggestedReplies);
}
