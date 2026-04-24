package com.telegram.codex.conversation.application.update;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StoredSuggestedRepliesParserTest {

    @Test
    void parseReturnsTrimmedRepliesFromStoredJson() {
        StoredSuggestedRepliesParser parser = new StoredSuggestedRepliesParser(new ObjectMapper());

        List<String> replies = parser.parse("[\" ok \", \"\", null, \"next\"]");

        assertEquals(List.of("ok", "next"), replies);
    }

    @Test
    void parseReturnsEmptyListForInvalidPayload() {
        StoredSuggestedRepliesParser parser = new StoredSuggestedRepliesParser(new ObjectMapper());

        assertEquals(List.of(), parser.parse("not-json"));
    }
}
