// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags;

import java.time.Duration;

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
    Codec<Duration> forDuration = from(Duration::toString, Duration::parse);

    static <U> Codec<U> from(Encoder<U> encoder, Decoder<U> decoder) {
        return new Codec<U>() {
            @Override public U decode(String value) { return decoder.decode(value); }
            @Override public String encode(U value) { return encoder.encode(value); }
        };
    }
}

