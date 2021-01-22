// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.logging;

import com.fasterxml.jackson.core.JsonGenerator;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;

/**
 * @author bjorncs
 */
class FormatUtil {

    private FormatUtil() {}

    static void writeSecondsField(JsonGenerator generator, String fieldName, Instant instant) throws IOException {
        writeSecondsField(generator, fieldName, instant.toEpochMilli());
    }

    static void writeSecondsField(JsonGenerator generator, String fieldName, Duration duration) throws IOException {
        writeSecondsField(generator, fieldName, duration.toMillis());
    }

    static void writeSecondsField(JsonGenerator generator, String fieldName, double seconds) throws IOException {
        writeSecondsField(generator, fieldName, (long)(seconds * 1000));
    }

    static void writeSecondsField(JsonGenerator generator, String fieldName, long milliseconds) throws IOException {
        generator.writeFieldName(fieldName);
        generator.writeRawValue(toSecondsString(milliseconds));
    }

    /** @return a string with number of seconds with 3 decimals */
    static String toSecondsString(long milliseconds) {
        StringBuilder builder = new StringBuilder().append(milliseconds / 1000L).append('.');
        long decimals = milliseconds % 1000;
        if (decimals < 100) {
            builder.append('0');
            if (decimals < 10) {
                builder.append('0');
            }
        }
        return builder.append(decimals).toString();
    }
}
