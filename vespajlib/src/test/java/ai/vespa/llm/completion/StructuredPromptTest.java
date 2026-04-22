// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.llm.completion;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StructuredPromptTest {

    @Test
    void renders_sections_in_order() {
        var prompt = StructuredPrompt.builder()
                                     .add("user-taste", "bags")
                                     .add("recent-actions", "view: <cb0_1>")
                                     .add("query", "leather messenger bag")
                                     .separator(" </s> ")
                                     .build();

        assertEquals("bags </s> view: <cb0_1> </s> leather messenger bag", prompt.asString());
    }

    @Test
    void append_text_updates_last_section() {
        var prompt = StructuredPrompt.builder()
                                     .add("query", "leather")
                                     .build()
                                     .append(" messenger bag");

        assertEquals("leather messenger bag", prompt.asString());
    }
}
