// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags;

import java.util.function.Predicate;

/**
 * Flags for custom string encodings.  For instance, a flag can be of the type java.time.Duration,
 * and it can be overridden with a JSON string parsed via Duration::parse.  Any failure to parse
 * the JSON string will prevent the override from being set.
 *
 * @author Håkon Hallingstad
 */
public class UnboundCodecFlag<T> extends UnboundFlagImpl<T, CodecFlag<T>, UnboundCodecFlag<T>> {

    public UnboundCodecFlag(FlagId id, T defaultValue, FetchVector defaultFetchVector, Codec<T> codec, Predicate<T> validator) {
        this(id, defaultValue, defaultFetchVector, serializerOf(id, codec, validator));
    }

    private UnboundCodecFlag(FlagId id, T defaultValue, FetchVector defaultFetchVector, FlagSerializer<T> serializer) {
        super(id,
              defaultValue,
              defaultFetchVector,
              serializer,
              (id_, defaultValue_, fetchVector_) -> new UnboundCodecFlag<>(id_, defaultValue_, fetchVector_, serializer),
              CodecFlag::new);
    }

    private static <T> FlagSerializer<T> serializerOf(FlagId id, Codec<T> codec, Predicate<T> validator) {
        return new FlagSerializer<>() {
            @Override public RawFlag serialize(T value) { return codec.serializer().serialize(value); }

            @Override public T deserialize(RawFlag rawFlag) {
                T value = codec.deserializer().deserialize(rawFlag);
                if (!validator.test(value))
                    throw new IllegalArgumentException("Invalid value of flag " + id + ": " + value);
                return value;
            }
        };
    }
}
