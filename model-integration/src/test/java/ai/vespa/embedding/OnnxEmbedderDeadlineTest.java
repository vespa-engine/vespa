// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.embedding;

import com.yahoo.language.process.Embedder;
import com.yahoo.language.process.InvocationContext;
import com.yahoo.language.process.TimeoutException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author glebashnik
 */
class OnnxEmbedderDeadlineTest {

    @Test
    void returns_empty_when_no_deadline_is_set() {
        var context = new Embedder.Context("test");
        assertEquals(Optional.empty(), OnnxEmbedderDeadline.deadlineOrThrow(context));
    }

    @Test
    void returns_the_deadline_when_it_is_in_the_future() {
        var deadline = Instant.now().plusSeconds(5);
        var context = new Embedder.Context("test").setDeadline(InvocationContext.Deadline.of(deadline));
        assertEquals(Optional.of(deadline), OnnxEmbedderDeadline.deadlineOrThrow(context));
    }

    @Test
    void throws_when_deadline_already_expired() {
        var context = new Embedder.Context("test")
                .setDeadline(InvocationContext.Deadline.of(Instant.now().minusSeconds(1)));
        var ex = assertThrows(TimeoutException.class, () -> OnnxEmbedderDeadline.deadlineOrThrow(context));
        assertEquals("Request deadline exceeded before ONNX evaluation", ex.getMessage());
    }
}
