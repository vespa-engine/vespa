// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import org.junit.jupiter.api.Test;

import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author glebashnik
 */
public class SidecarSpecTest {

    private static SidecarSpec.Builder builder() {
        return SidecarSpec.builder()
                .id(0)
                .name("triton")
                .image(DockerImage.fromString("registry.example.com/repo/image:1.0"));
    }

    @Test
    void shm_size_defaults_to_the_container_runtime_default() {
        assertEquals(OptionalInt.empty(), builder().build().shmSizeMiB());
    }

    @Test
    void shm_size_is_kept_by_the_builder() {
        var spec = builder().shmSizeMiB(1024).build();
        assertEquals(OptionalInt.of(1024), spec.shmSizeMiB());
        assertEquals(spec, spec.toBuilder().build());
    }

    @Test
    void shm_size_must_be_positive() {
        assertThrows(IllegalArgumentException.class, () -> builder().shmSizeMiB(0).build());
    }

}
