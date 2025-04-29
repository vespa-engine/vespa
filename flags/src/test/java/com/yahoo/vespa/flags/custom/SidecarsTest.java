package com.yahoo.vespa.flags.custom;

import com.yahoo.test.json.Jackson;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SidecarsTest {
    @Test
    void testWithAllValue() throws IOException {
        verifySerialization(new Sidecars(List.of(
                new Sidecar(
                        "triton",
                        new SidecarImage("nginx:alpine"), 
                        new SidecarQuota(1.0, "8Gb", "all")))));
    }

    @Test
    void testWithNulls() throws IOException {
        verifySerialization(new Sidecars(List.of(
                new Sidecar(
                        "triton",
                        new SidecarImage("nginx:alpine"),
                        new SidecarQuota(null, "8Gb", null)))));
    }

    @Test
    void testDisabled() throws IOException {
        verifySerialization(Sidecars.createDisabled());
    }

    private void verifySerialization(Sidecars sidecars) throws IOException {
        var mapper = Jackson.mapper();
        String json = mapper.writeValueAsString(sidecars);
        var deserialized = mapper.readValue(json, Sidecars.class);
        assertEquals(sidecars, deserialized);
    }
}
