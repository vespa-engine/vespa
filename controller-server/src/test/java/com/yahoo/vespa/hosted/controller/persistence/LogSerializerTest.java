// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.persistence;

import com.yahoo.vespa.hosted.controller.api.integration.LogEntry;
import com.yahoo.vespa.hosted.controller.deployment.Step;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.yahoo.vespa.hosted.controller.deployment.Step.deployReal;
import static com.yahoo.vespa.hosted.controller.deployment.Step.deployTester;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author jonmv
 */
public class LogSerializerTest {

    private static final LogSerializer serializer = new LogSerializer();
    private static final Path logsFile = Paths.get("src/test/java/com/yahoo/vespa/hosted/controller/persistence/testdata/logs.json");

    @Test
    void testSerialization() throws IOException {
        for (LogEntry.Type type : LogEntry.Type.values())
            assertEquals(type, LogSerializer.typeOf(LogSerializer.valueOf(type)));

        byte[] logJson = Files.readAllBytes(logsFile);

        LogEntry  first = new LogEntry(0, Instant.ofEpochMilli(0), LogEntry.Type.info, "First");
        LogEntry second = new LogEntry(1, Instant.ofEpochMilli(0), LogEntry.Type.info, "Second");
        LogEntry  third = new LogEntry(2, Instant.ofEpochMilli(1000), LogEntry.Type.debug, "Third");
        LogEntry fourth = new LogEntry(3, Instant.ofEpochMilli(2000), LogEntry.Type.warning, "Fourth");

        Map<Step, List<LogEntry>> expected = new HashMap<>();
        expected.put(deployReal, new ArrayList<>());
        expected.get(deployReal).add(third);
        expected.put(deployTester, new ArrayList<>());
        expected.get(deployTester).add(fourth);

        assertEquals(expected, serializer.fromJson(logJson, 1));

        expected.get(deployReal).add(0, first);
        expected.get(deployTester).add(0, second);
        assertEquals(expected, serializer.fromJson(logJson, -1));

        assertEquals(expected, serializer.fromJson(serializer.toJson(expected), -1));

        expected.get(deployReal).add(first);
        expected.get(deployReal).add(third);
        expected.get(deployTester).add(second);
        expected.get(deployTester).add(fourth);

        assertEquals(expected, serializer.fromJson(List.of(logJson, logJson), -1));
    }

}
