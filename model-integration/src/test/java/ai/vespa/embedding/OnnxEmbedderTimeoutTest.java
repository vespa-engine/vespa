// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.embedding;

import com.yahoo.language.process.Embedder;
import com.yahoo.language.process.InvocationContext;
import com.yahoo.language.process.TimeoutException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author glebashnik
 */
class OnnxEmbedderTimeoutTest {

    @Test
    void returns_null_when_no_deadline_is_set() {
        var context = new Embedder.Context("test");
        assertNull(OnnxEmbedderTimeout.remainingOrThrow(context));
    }

    @Test
    void returns_remaining_duration_when_deadline_is_in_the_future() {
        var context = new Embedder.Context("test")
                .setDeadline(InvocationContext.Deadline.of(Duration.ofSeconds(5)));
        var remaining = OnnxEmbedderTimeout.remainingOrThrow(context);
        assertTrue(remaining != null && remaining.toMillis() > 0,
                "expected a positive remaining duration, got " + remaining);
        assertTrue(remaining.toMillis() <= 5_000,
                "remaining must not exceed deadline budget, got " + remaining.toMillis() + " ms");
    }

    @Test
    void throws_when_deadline_already_expired() {
        var context = new Embedder.Context("test")
                .setDeadline(InvocationContext.Deadline.of(Instant.now().minusSeconds(1)));
        var ex = assertThrows(TimeoutException.class, () -> OnnxEmbedderTimeout.remainingOrThrow(context));
        assertEquals("Request deadline exceeded before ONNX evaluation", ex.getMessage());
    }
}
