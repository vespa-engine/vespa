package com.yahoo.vespa.hosted.node.admin.maintenance.servicedump;// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

import com.yahoo.yolean.concurrent.Sleeper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author bjorncs
 */
class ArtifactProducersTest {

    @Test
    void generates_exception_on_unknown_artifact() {
        ArtifactProducers instance = ArtifactProducers.createDefault(Sleeper.NOOP);
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class, () -> instance.resolve(List.of("unknown-artifact")));
        String expectedMsg = "Invalid artifact type 'unknown-artifact'. " +
                "Valid types are ['perf-report', 'jfr-recording'] and valid aliases are []";
        assertEquals(expectedMsg, exception.getMessage());
    }

}