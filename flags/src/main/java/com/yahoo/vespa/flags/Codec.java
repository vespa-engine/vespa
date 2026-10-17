// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags;

import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.function.Predicate;

/**
 * Decodes a String to a Java instance of a specific type, and vice versa.
 *
 * <p>Similar to {@link Deserializer} ({@link Serializer}), except these are for
 * deserializing (serializing to) flag data, while Codec is for deserializing (serializing to)
 * a JSON string value, for instance creating a java.time.Duration from "PT2H".</p>
 *
 * @author Håkon Hallingstad
 */
public interface Codec<T> extends Encoder<T>, Decoder<T> {

    /** Durations of the form "PnDTnHnMn.nS" as accepted by java.time.Duration.parse(), e.g. PT2H30M and PT1.234S. */
    Codec<Duration> forDuration = from(Duration::toString, string -> {
        try {
            return Duration.parse(string);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Failed to parse '" + string + "' to java.time.Duration", e);
        }
    });

    static <U> Codec<U> from(Encoder<U> encoder, Decoder<U> decoder) {
        return new Codec<U>() {
            @Override public U decode(String value) { return decoder.decode(value); }
            @Override public String encode(U value) { return encoder.encode(value); }
        };
    }

    default FlagSerializer<T> toFlagSerializer() { return toFlagSerializer(__ -> true); }

    default FlagSerializer<T> toFlagSerializer(Predicate<T> validator) {
        return new  FlagSerializer<>() {
            @Override
            public T deserialize(RawFlag rawFlag) {
                T value = Codec.this.deserializer().deserialize(rawFlag);
                if (!validator.test(value)) {
                    throw new IllegalArgumentException("Invalid value: " + rawFlag.asJson());
                }
                return value;
            }

            @Override
            public RawFlag serialize(T value) { return Codec.this.serializer().serialize(value); }
        };
    }
}

