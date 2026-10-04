// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;

import java.time.Duration;
import java.util.function.Predicate;

/**
 * Flags for custom string encodings.  For instance, a flag can be of the type java.time.Duration,
 * and it can be overridden with a JSON string parsed via Duration::parse.  Any failure to parse
 * the JSON string will prevent the override from being set.
 *
 * @author Håkon Hallingstad
 */
public class UnboundCodecFlag<T> extends UnboundFlagImpl<T, CodecFlag<T>, UnboundCodecFlag<T>> {

    public interface Encoder<T> {  String encode(T value); }
    public interface Decoder<T> { T decode(String value); }

    public interface Codec<T> extends Encoder<T>, Decoder<T> {

        /** Durations of the form "PnDTnHnMn.nS" as accepted by java.time.Duration.parse(), e.g. PT2H30M and PT1.234S. */
        Codec<Duration> DURATION = from(Duration::toString, Duration::parse);

        static <U> Codec<U> from(Encoder<U> encoder, Decoder<U> decoder) {
            return new Codec<U>() {
                @Override public U decode(String value) { return decoder.decode(value); }
                @Override public String encode(U value) { return encoder.encode(value); }
            };
        }
    }

    public UnboundCodecFlag(FlagId id,
                            T defaultValue,
                            FetchVector defaultFetchVector,
                            Codec<T> codec,
                            Predicate<T> validator) {
        this(id,
             defaultValue,
             defaultFetchVector,
             new SimpleFlagSerializer<>(object -> new TextNode(codec.encode(object)),
                                        JsonNode::isTextual,
                                        jsonNode -> {
                                            T value = codec.decode(jsonNode.asText());
                                            if (!validator.test(value))
                                                throw new IllegalArgumentException("Invalid value of flag " + id + ": " + value);
                                            return value;
                                        }));
    }

    private UnboundCodecFlag(FlagId id, T defaultValue, FetchVector defaultFetchVector, FlagSerializer<T> serializer) {
        super(id,
              defaultValue,
              defaultFetchVector,
              serializer,
              (id_, defaultValue_, fetchVector_) -> new UnboundCodecFlag<>(id_, defaultValue_, fetchVector_, serializer),
              CodecFlag::new);
    }
}
