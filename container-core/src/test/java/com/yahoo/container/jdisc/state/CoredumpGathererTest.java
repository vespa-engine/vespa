// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc.state;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;


/**
 * @author olaa
 */
public class CoredumpGathererTest {

    @Test
    void finds_one_coredump()  {
        JsonNode packet = CoredumpGatherer.gatherCoredumpMetrics(new MockFileWrapper());

        assertEquals("system-coredumps-processing", packet.get("application").textValue());
        assertEquals(1, packet.get("status_code").intValue());
        assertEquals("Found 1 coredump(s)", packet.get("status_msg").textValue());

    }

    static class MockFileWrapper extends FileWrapper {


        @Override
        Stream<Path> walkTree(Path path)  {
            return Stream.of(Path.of("dummy-path"));
        }

        @Override
        boolean isRegularFile(Path path) {
            return true;
        }
    }

}
