// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.embedding;

import com.yahoo.language.process.Embedder;
import com.yahoo.language.process.TimeoutException;

import java.time.Instant;
import java.util.Optional;

/**
 * Resolves the deadline of an ONNX evaluation from an {@link Embedder.Context}, for use by
 * ONNX-backed embedders.
 *
 * @author glebashnik
 */
final class OnnxEmbedderDeadline {

    private OnnxEmbedderDeadline() {}

    /**
     * Returns the deadline of the request, or empty if no deadline is set.
     * An ONNX runtime may not stop an evaluation at the deadline, so an evaluation is not started after it.
     *
     * @throws TimeoutException if the deadline has already expired
     */
    static Optional<Instant> deadlineOrThrow(Embedder.Context context) {
        var deadline = context.getDeadline();
        if (deadline.isEmpty()) return Optional.empty();
        if (deadline.get().timeRemaining().toMillis() <= 0)
            throw new TimeoutException("Request deadline exceeded before ONNX evaluation");
        return Optional.of(deadline.get().asInstant());
    }
}
