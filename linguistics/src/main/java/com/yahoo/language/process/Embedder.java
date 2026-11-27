// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.language.process;

import com.yahoo.api.annotations.Beta;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * An embedder converts a text string, or an input with a text, an image or both, to a tensor
 *
 * @author bratseth
 * @author glebashnik
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

    /**
     * Same as {@link #embed(String, Context, TensorType)}, but operates on a list of text strings.
     * This will typically be the chunks of a multi-value document field.
     * Embedders supporting contextualized chunk embeddings or batch processing may override this method.
     *
     * @see #embed(String, Context, TensorType)
     */
    default List<Tensor> embed(List<String> texts, Context context, TensorType tensorType) {
        return texts.stream()
                .map(text -> embed(text, context, tensorType))
                .toList();
    }

    /**
     * Converts input into a tensor.
     * An embedder which accepts an image overrides this method and {@link #supportedModalities()}.
     * For a text alone, this must give the same tensor as {@link #embed(String, Context, TensorType)}.
     *
     * @throws IllegalArgumentException if this embedder does not accept the input
     */
    @Beta
    default Tensor embed(Input input, Context context, TensorType tensorType) {
        if (input.image().isEmpty())
            return embed(input.text().get(), context, tensorType);
        throw new IllegalArgumentException("This embedder accepts text, but is given " + input);
    }

    /**
     * Returns the combinations of modalities this embedder accepts in one input.
     * E.g. an embedder which embeds a text, an image, or a text with an image returns
     * {@code Set.of(Set.of(TEXT), Set.of(IMAGE), Set.of(TEXT, IMAGE))}.
     * A caller must only give this embedder input which this embedder accepts, see {@link #rejectionReason}.
     */
    @Beta
    default Set<Set<Modality>> supportedModalities() { return Set.of(Set.of(Modality.TEXT)); }

    /** Returns why an embedder rejects input, or empty if the embedder accepts the input. */
    @Beta
    static Optional<String> rejectionReason(Set<Modality> inputModalities, Set<Set<Modality>> supportedModalities) {
        if (supportedModalities.contains(inputModalities)) return Optional.empty();
        // A set has no order, and the message must be the same every time
        String accepted = supportedModalities.stream().map(Embedder::describe).sorted()
                                             .collect(Collectors.joining(" or "));
        return Optional.of("accepts " + accepted + ", but is given " + describe(inputModalities));
    }

    private static String describe(Set<Modality> modalities) {
        return new TreeSet<>(modalities).stream().map(Modality::toString).collect(Collectors.joining(" and "));
    }

    /** A modality of input to an embedder. */
    @Beta
    enum Modality {

        TEXT, IMAGE;

        @Override
        public String toString() { return name().toLowerCase(Locale.ROOT); }

    }

    /** The input to an embedder: A text, an image, or both. */
    @Beta
    record Input(Optional<String> text, Optional<Image> image) {

        public Input {
            if (text.isEmpty() && image.isEmpty())
                throw new IllegalArgumentException("Embedder input must have a text or an image");
        }

        public static Input of(String text) { return new Input(Optional.of(text), Optional.empty()); }

        public static Input of(Image image) { return new Input(Optional.empty(), Optional.of(image)); }

        public static Input of(String text, Image image) { return new Input(Optional.of(text), Optional.of(image)); }

        public Set<Modality> modalities() {
            Set<Modality> modalities = EnumSet.noneOf(Modality.class);
            if (text.isPresent()) modalities.add(Modality.TEXT);
            if (image.isPresent()) modalities.add(Modality.IMAGE);
            return modalities;
        }

        @Override
        public String toString() { return describe(modalities()); }

    }

    /** An encoded image, in one of the formats JPEG, PNG and GIF. */
    @Beta
    record Image(ByteBuffer data) {

        /** @throws IllegalArgumentException if the data is not an image in one of the formats of this type */
        public Image {
            data = data.asReadOnlyBuffer();
            if (mediaTypeOf(data).isEmpty())
                throw new IllegalArgumentException("Binary data to embed must be a JPEG, PNG or GIF image");
        }

        public Image(byte[] data) { this(ByteBuffer.wrap(data)); }

        /**
         * Returns a duplicate, since reading a buffer moves its position,
         * and equals and hashCode use the bytes after the position.
         */
        @Override
        public ByteBuffer data() { return data.duplicate(); }

        /** Returns the media type of this image, detected from its first bytes, e.g. "image/jpeg". */
        public String mediaType() { return mediaTypeOf(data).get(); }

        private static Optional<String> mediaTypeOf(ByteBuffer data) {
            if (startsWith(data, 0xFF, 0xD8, 0xFF)) return Optional.of("image/jpeg");
            if (startsWith(data, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) return Optional.of("image/png");
            if (startsWith(data, 'G', 'I', 'F', '8')) return Optional.of("image/gif");
            return Optional.empty();
        }

        private static boolean startsWith(ByteBuffer data, int... bytes) {
            if (data.remaining() < bytes.length) return false;
            for (int i = 0; i < bytes.length; i++) {
                if ((data.get(data.position() + i) & 0xFF) != bytes[i]) return false;
            }
            return true;
        }

        @Override
        public String toString() { return "image (" + data.remaining() + " bytes)"; }

    }

    /** Batching configuration of embed requests. */
    record Batching(int maxSize, Duration maxDelay) {
        public static final Batching DISABLED = new Batching(0, Duration.ZERO);
        public Batching {
            if (maxSize < 0) throw new IllegalArgumentException("maxSize must be non-negative, got " + maxSize);
            if (maxDelay.isNegative()) throw new IllegalArgumentException("maxDelay must be non-negative, got " + maxDelay);
        }
        public boolean isEnabled() { return maxSize > 1; }
        public static Batching of(int maxSize, Duration maxDelay) {
            if (maxSize <= 1 || maxDelay.isZero() || maxDelay.isNegative()) return DISABLED;
            return new Batching(maxSize, maxDelay);
        }
    }

    default Batching batchingConfig() { return Batching.DISABLED; }

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
        /**
         * Add a sample padded embedding length to this.
         * Padding to padding-buckets makes inputs of different lengths the same length, so they can be batched.
         * Without padding, this is the input length.
         */
        default void samplePaddedSequenceLength(long length, Context ctx) { }
        /** Add a sample request count to this */
        void sampleRequestCount(Context ctx);
        /** Add a sample request failure to this */
        void sampleRequestFailure(Context ctx, int statusCode);

        static Runtime testInstance() {
            return new Runtime() {
                @Override public void sampleEmbeddingLatency(double millis, Context ctx) { }
                @Override public void sampleSequenceLength(long length, Context ctx) { }
                @Override public void sampleRequestCount(Context ctx) { }
                @Override public void sampleRequestFailure(Context ctx, int statusCode) { }
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
        public Tensor embed(Input input, Context context, TensorType tensorType) {
            throw new IllegalStateException(message);
        }

        /** This embedder stands in for an embedder whose accepted input is not known. */
        @Override
        public Set<Set<Modality>> supportedModalities() {
            return Set.of(Set.of(Modality.TEXT), Set.of(Modality.IMAGE), Set.of(Modality.TEXT, Modality.IMAGE));
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
