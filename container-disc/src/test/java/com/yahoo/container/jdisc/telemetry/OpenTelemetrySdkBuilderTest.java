// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc.telemetry;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * @author onur
 */
class OpenTelemetrySdkBuilderTest {

    @Test
    void builds_endpoint_from_hostname_file(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("host-hostname");
        Files.writeString(file, "host1.example.com\n");
        assertEquals("https://host1.example.com:4318/v1/traces", OpenTelemetrySdkBuilder.resolveEndpoint(file));
    }

    @Test
    void returns_null_when_file_missing(@TempDir Path dir) {
        assertNull(OpenTelemetrySdkBuilder.resolveEndpoint(dir.resolve("absent")));
    }

    @Test
    void returns_null_when_file_blank(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("host-hostname");
        Files.writeString(file, "  \n");
        assertNull(OpenTelemetrySdkBuilder.resolveEndpoint(file));
    }

}
