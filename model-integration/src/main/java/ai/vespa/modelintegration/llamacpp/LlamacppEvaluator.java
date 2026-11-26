// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.modelintegration.llamacpp;

import com.yahoo.language.process.Embedder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * A GGUF embedding model loaded by a {@link LlamacppRuntime}.
 * An empty deadline means the runtime's default timeout; a runtime in the container ignores the deadline.
 *
 * @author ingridhabrekke
 */
public interface LlamacppEvaluator extends AutoCloseable {

    /** A part of an input to embed: a text or an image. */
    sealed interface Part {
        record Text(String text) implements Part { }
        record Image(Embedder.Image image) implements Part { }
    }

    /** Returns whether this evaluator embeds images, which needs a multimodal projector. */
    default boolean acceptsImages() { return false; }

    /**
     * Returns one embedding of the parts, which the model reads in the given order.
     *
     * @throws UnsupportedOperationException if this evaluator does not embed images
     */
    default float[] embed(List<Part> parts, Optional<Instant> deadline) {
        throw new UnsupportedOperationException("This llama.cpp evaluator embeds text only");
    }

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
