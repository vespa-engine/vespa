// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.language.process;

import com.yahoo.api.annotations.Beta;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * An embedder converts a text string to a tensor
 *
 * @author bratseth
 */
public interface Embedder {

    /** ID of embedder when none is explicitly given */
    String defaultEmbedderId = "default";

    /** An instance of this which throws IllegalStateException if attempted used */
    Embedder throwsOnUse = new FailingEmbedder();

    /** Returns this embedder instance as a map with the default embedder name */
    default Map<String, Embedder> asMap() {
        return asMap(defaultEmbedderId);
    }

    /** Returns this embedder instance as a map with the given name */
    default Map<String, Embedder> asMap(String name) {
        return Map.of(name, this);
    }

    /**
     * Converts text into a list of token id's (a vector embedding)
     *
     * @param text the text to embed
     * @param context the context which may influence an embedder's behavior
     * @return the text embedded as a list of token ids
     * @throws IllegalArgumentException if the language is not supported by this embedder
     */
    List<Integer> embed(String text, Context context);

    /**
     * Converts the list of token id's into a text. The opposite operation of embed.
     *
     * @param tokens the list of tokens to decode to a string
     * @param context the context which specifies the language used to select a model
     * @return the string formed by decoding the tokens back to their string repreesentation
     */
    default String decode(List<Integer> tokens, Context context) {
        throw new UnsupportedOperationException("Decode is not implemented");
    }

    /**
     * Converts text into tokens in a tensor.
     * The information contained in the embedding may depend on the tensor type.
     *
     * @param text the text to embed
     * @param context the context which may influence an embedder's behavior
     * @param tensorType the type of the tensor to be returned
     * @return the tensor embedding of the text, as the specified tensor type
     * @throws IllegalArgumentException if the language or tensor type is not supported by this embedder
     */
    Tensor embed(String text, Context context, TensorType tensorType);

    class Context extends InvocationContext<Context> {

        public Context(String destination) {
            super(destination);
        }

        public Context(String destination, Map<Object, Object> cache) {
            super(destination, cache);
        }

        public Context(Context other) {
            super(other);
        }

        public Context copy() {
            return new Context(this);
        }

        /** Return the component id or 'unknown' if not set. */
        public String getEmbedderId() { return getComponentId(); }

        /** Sets the component id. */
        public Context setEmbedderId(String componentId) {
            return setComponentId(componentId);
        }

    }

    /**
     * Runtime that is injectable through {@link Embedder} constructor.
     */
    @Beta
    interface Runtime {

        /** Add a sample embedding latency to this */
        void sampleEmbeddingLatency(double millis, Context ctx);
        /** Add a sample embedding length to this */
        void sampleSequenceLength(long length, Context ctx);

        static Runtime testInstance() {
            return new Runtime() {
                @Override public void sampleEmbeddingLatency(double millis, Context ctx) { }
                @Override public void sampleSequenceLength(long length, Context ctx) { }
            };
        }
    }

    class FailingEmbedder implements Embedder {

        private final String message;

        public FailingEmbedder() {
            this("No embedder has been configured");
        }

        public FailingEmbedder(String message) {
            this.message = message;
        }

        @Override
        public List<Integer> embed(String text, Context context) {
            throw new IllegalStateException(message);
        }

        @Override
        public Tensor embed(String text, Context context, TensorType tensorType) {
            throw new IllegalStateException(message);
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof FailingEmbedder;
        }

        @Override
        public int hashCode() {
            return getClass().getName().hashCode();
        }

        public static Function<String, Embedder> factory() {
            return FailingEmbedder::new;
        }

    }

}
