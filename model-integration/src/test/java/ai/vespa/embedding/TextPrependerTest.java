// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.embedding;

import com.yahoo.language.process.Embedder;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * @author bjorncs
 */
class TextPrependerTest {

    private static final Embedder.Context QUERY = new Embedder.Context("query(qt)");
    private static final Embedder.Context DOCUMENT = new Embedder.Context("schema.indexing");

    @Test
    void prependQuery_only_appliesToQueryContext_notDocument() {
        var prepender = new TextPrepender("query: ", "");
        assertEquals("query: hello", prepender.prepend("hello", QUERY));
        assertEquals("hello", prepender.prepend("hello", DOCUMENT));
    }

    @Test
    void prependDocument_only_appliesToDocumentContext_notQuery() {
        var prepender = new TextPrepender("", "passage: ");
        assertEquals("hello", prepender.prepend("hello", QUERY));
        assertEquals("passage: hello", prepender.prepend("hello", DOCUMENT));
    }

    @Test
    void both_configured_appliesIndependently() {
        var prepender = new TextPrepender("query: ", "passage: ");
        assertEquals("query: hello", prepender.prepend("hello", QUERY));
        assertEquals("passage: hello", prepender.prepend("hello", DOCUMENT));
    }

    @Test
    void prependAll_returnsSameListInstance_whenNoPrefixApplies() {
        var texts = List.of("a", "b");
        assertSame(texts, new TextPrepender("", "").prependAll(texts, QUERY));
        assertSame(texts, new TextPrepender("", "passage: ").prependAll(texts, QUERY));
    }

    @Test
    void prependAll_appliesPrefixToEveryElement() {
        var prepender = new TextPrepender("query: ", "passage: ");
        assertEquals(List.of("query: a", "query: b"), prepender.prependAll(List.of("a", "b"), QUERY));
        assertEquals(List.of("passage: a", "passage: b"), prepender.prependAll(List.of("a", "b"), DOCUMENT));
    }
}
