// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags.custom;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SharedHostTest {
    @Test
    void serialization() throws IOException {
        verifySerialization(new SharedHost(List.of(
                new HostResources(1.0, 2.0, 3.0, 4.0, "fast", "remote",
                        "container", 5, "x86_64"))));
        verifySerialization(new SharedHost(List.of(
                new HostResources(1.0, 2.0, 3.0, 4.0, "fast", "remote",
                        "admin", 5, "arm64"))));
    }

    private void verifySerialization(SharedHost sharedHost) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(sharedHost);
        SharedHost deserialized = mapper.readValue(json, SharedHost.class);
        assertEquals(sharedHost, deserialized);
    }
}