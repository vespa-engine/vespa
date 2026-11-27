// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.modelintegration.llamacpp;

import java.time.Instant;
import java.util.Optional;

/**
 * A GGUF embedding model loaded by a {@link LlamacppRuntime}.
 * An empty deadline means the runtime's default timeout; {@link EmbeddedLlamacppRuntime} ignores the deadline.
 *
 * @author ingridhabrekke
 * @author glebashnik
 */
public interface LlamacppEvaluator extends AutoCloseable {

    /**
     * Returns the embedding of the text, as the model pools it.
     * llama.cpp normalizes a pooled embedding to unit length (L2 norm).
     *
     * @throws IllegalArgumentException if the text is too long for the model's batch or context size
     */
    float[] embed(String text, Optional<Instant> deadline);

    /** Returns the model's token ids of the text, without special tokens such as start and end tokens. */
    int[] tokenize(String text, Optional<Instant> deadline);

    /** Returns the text of the token ids. */
    String detokenize(int[] tokens, Optional<Instant> deadline);

    /** Releases the model. */
    @Override
    void close();

}
