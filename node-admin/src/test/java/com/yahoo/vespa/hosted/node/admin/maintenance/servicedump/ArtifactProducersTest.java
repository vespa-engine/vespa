// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.maintenance.servicedump;

import com.yahoo.yolean.concurrent.Sleeper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author bjorncs
 */
class ArtifactProducersTest {

    @Test
    void generates_exception_on_unknown_artifact() {
        ArtifactProducers instance = ArtifactProducers.createDefault(Sleeper.NOOP);
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class, () -> instance.resolve(List.of("unknown-artifact")));
        String expectedMsg =
                "Invalid artifact type 'unknown-artifact'. Valid types are " +
                        "['jvm-heap-dump', 'jvm-jfr', 'jvm-jmap', 'jvm-jstack', 'jvm-jstat', 'perf-report', 'pmap', " +
                        "'vespa-log', 'zookeeper-snapshot'] " +
                        "and valid aliases are " +
                        "['jvm-dump': ['jvm-heap-dump', 'jvm-jmap', 'jvm-jstack', 'jvm-jstat', 'vespa-log']]";
        assertEquals(expectedMsg, exception.getMessage());
    }

}
