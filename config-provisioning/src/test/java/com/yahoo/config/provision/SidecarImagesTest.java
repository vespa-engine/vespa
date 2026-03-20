// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class SidecarImagesTest {

    @Test
    void test_getOrThrow_returns_known_key() {
        var images = SidecarImages.readFromProperties();
        var triton = images.getOrThrow("triton");
        assertEquals("nvidia/tritonserver", triton.repository());
        assertEquals("nvcr.io", triton.registry());
    }

    @Test
    void test_getOrThrow_throws_unknown_key() {
        var images = SidecarImages.readFromProperties();
        var ex = assertThrows(IllegalStateException.class, () -> images.getOrThrow("unknown"));
        assertEquals("Sidecar image 'unknown' is not configured in sidecar-images.properties", ex.getMessage());
    }

    @Test
    void test_getByRepositoryOrThrow_returns_known_repository() {
        var images = SidecarImages.readFromProperties();
        var triton = images.getByRepositoryOrThrow("nvidia/tritonserver");
        assertEquals("nvcr.io", triton.registry());
    }

    @Test
    void test_getByRepositoryOrThrow_throws_unknown_repository() {
        var images = SidecarImages.readFromProperties();
        var ex = assertThrows(IllegalStateException.class, () -> images.getByRepositoryOrThrow("unknown/repo"));
        assertEquals(
                "No sidecar image with repository 'unknown/repo' configured in sidecar-images.properties",
                ex.getMessage());
    }

    @Test
    void test_removeSidecarRepositoryPrefix_returns_source_repository() {
        assertEquals("nvidia/tritonserver", SidecarImages.removeSidecarRepositoryPrefix("sidecar/nvidia/tritonserver"));
    }

    @Test
    void test_removeSidecarRepositoryPrefix_throws_wrong_prefix() {
        var ex = assertThrows(
                IllegalArgumentException.class, () -> SidecarImages.removeSidecarRepositoryPrefix("nvidia/tritonserver"));
        assertEquals(
                "Sidecar repository 'nvidia/tritonserver' does not start with expected prefix 'sidecar/'",
                ex.getMessage());
    }
}
