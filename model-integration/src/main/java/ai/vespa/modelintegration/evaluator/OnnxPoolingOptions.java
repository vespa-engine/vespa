// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.modelintegration.evaluator;

import java.util.Objects;

/**
 * Asks the runtime to pool the token embeddings of a model output. Evaluation then returns
 * [batch, embedding] instead of [batch, sequence, embedding].
 * A runtime may ignore the request. {@link OnnxEvaluator#runtimePooling()} tells whether an evaluator applies it.
 *
 * @param strategy          how the runtime combines the token embeddings
 * @param outputName        the model output with the token embeddings, which the runtime replaces with the pooled embeddings
 * @param attentionMaskName the model input with the attention mask, which mean and last-token pooling use
 * @author glebashnik
 */
public record OnnxPoolingOptions(Strategy strategy, String outputName, String attentionMaskName) {

    public enum Strategy {

        /** Averages the token embeddings the attention mask covers */
        MEAN("mean"),
        /** Takes the embedding of the first token */
        CLS("cls"),
        /** Takes the embedding of the last token the attention mask covers */
        LAST("last");

        private final String configName;

        Strategy(String configName) { this.configName = configName; }

        /** Returns the name of this strategy in embedder configs and in Triton model configs */
        public String configName() { return configName; }

    }

    public OnnxPoolingOptions {
        Objects.requireNonNull(strategy, "strategy cannot be null");
        Objects.requireNonNull(outputName, "outputName cannot be null");
        Objects.requireNonNull(attentionMaskName, "attentionMaskName cannot be null");
    }
}
