// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.embedding;

import com.yahoo.language.process.Embedder;
import com.yahoo.language.process.TimeoutException;

import java.time.Duration;

/**
 * Resolves a per-call timeout from an {@link Embedder.Context} deadline, for use by
 * ONNX-backed embedders.
 *
 * @author glebashnik
 */
final class OnnxEmbedderTimeout {

    private OnnxEmbedderTimeout() {}

    /**
     * Returns the time remaining until the request deadline, or {@code null} if no deadline is set.
     *
     * @throws TimeoutException if the deadline has already expired
     */
    static Duration remainingOrThrow(Embedder.Context context) {
        var deadline = context.getDeadline().orElse(null);
        if (deadline == null) return null;
        long remainingMs = deadline.timeRemaining().toMillis();
        if (remainingMs <= 0)
            throw new TimeoutException("Request deadline exceeded before ONNX evaluation");
        return Duration.ofMillis(remainingMs);
    }
}
