// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags;

import com.yahoo.yolean.Exceptions;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author Håkon Hallingstad
 */
class CodecTest {
    @Test
    void testDuration() {
        verify("PT2H30M", Duration.ofHours(2).plus(Duration.ofMinutes(30)));
        verify("PT1.234S", Duration.ofMillis(1234));
    }

    @Test
    void deserializerNamesTheInvalidText() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                                                  () -> Codec.forDuration.deserializer().deserialize(JsonNodeRawFlag.fromJson("\"PT3D\"")));
        assertEquals("Failed to parse 'PT3D' to java.time.Duration", e.getMessage());
        assertEquals("Failed to parse 'PT3D' to java.time.Duration: Text cannot be parsed to a Duration", Exceptions.toMessageString(e));
    }

    private void verify(String serialized, Duration duration) {
        verify(serialized, duration, Codec.forDuration);
    }

    /** This assumes equals() can be invoked on T. */
    private static <T> void verify(String serialized, T value, Codec<T> codec) {
        assertEquals(serialized, codec.encode(value));
        assertEquals(value, codec.decode(serialized));
    }
}
