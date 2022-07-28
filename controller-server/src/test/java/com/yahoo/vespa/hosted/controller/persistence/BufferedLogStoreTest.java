// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.persistence;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.SystemName;
import com.yahoo.vespa.hosted.controller.api.integration.LogEntry;
import com.yahoo.vespa.hosted.controller.api.integration.RunDataStore;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.RunId;
import com.yahoo.vespa.hosted.controller.api.integration.stubs.MockRunDataStore;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentContext;
import com.yahoo.vespa.hosted.controller.deployment.RunLog;
import com.yahoo.vespa.hosted.controller.deployment.Step;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toUnmodifiableList;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class BufferedLogStoreTest {

    @Test
    void chunkingAndFlush() {
        int chunkSize = 1 << 10;
        int maxChunks = 1 << 5;
        CuratorDb buffer = new MockCuratorDb(SystemName.main);
        RunDataStore store = new MockRunDataStore();
        BufferedLogStore logs = new BufferedLogStore(chunkSize, chunkSize * maxChunks, buffer, store);
        RunId id = new RunId(ApplicationId.from("tenant", "application", "instance"),
                DeploymentContext.productionUsWest1,
                123);

        byte[] manyBytes = new byte[chunkSize / 2 + 1]; // One fits, and two (over-)fills.
        Arrays.fill(manyBytes, (byte) 'O');
        LogEntry entry = new LogEntry(0, Instant.ofEpochMilli(123), LogEntry.Type.warning, new String(manyBytes));

        // Log entries are re-sequenced by the log store, by enumeration.
        LogEntry entry0 = new LogEntry(0, entry.at(), entry.type(), entry.message());
        LogEntry entry1 = new LogEntry(1, entry.at(), entry.type(), entry.message());
        LogEntry entry2 = new LogEntry(2, entry.at(), entry.type(), entry.message());
        LogEntry entry3 = new LogEntry(3, entry.at(), entry.type(), entry.message());
        LogEntry entry4 = new LogEntry(4, entry.at(), entry.type(), entry.message());

        assertEquals(Optional.empty(), logs.readFinished(id, -1));
        assertEquals(RunLog.empty(), logs.readActive(id.application(), id.type(), -1));

        logs.append(id.application(), id.type(), Step.deployReal, List.of(entry), false);
        assertEquals(List.of(entry0),
                logs.readActive(id.application(), id.type(), -1).get(Step.deployReal));
        assertEquals(RunLog.empty(), logs.readActive(id.application(), id.type(), 0));

        logs.append(id.application(), id.type(), Step.deployReal, List.of(entry), false);
        assertEquals(List.of(entry0, entry1),
                logs.readActive(id.application(), id.type(), -1).get(Step.deployReal));
        assertEquals(List.of(entry1),
                logs.readActive(id.application(), id.type(), 0).get(Step.deployReal));
        assertEquals(RunLog.empty(), logs.readActive(id.application(), id.type(), 1));

        logs.append(id.application(), id.type(), Step.deployReal, List.of(entry, entry, entry), false);
        assertEquals(List.of(entry0, entry1, entry2, entry3, entry4),
                logs.readActive(id.application(), id.type(), -1).get(Step.deployReal));
        assertEquals(List.of(entry1, entry2, entry3, entry4),
                logs.readActive(id.application(), id.type(), 0).get(Step.deployReal));
        assertEquals(List.of(entry2, entry3, entry4),
                logs.readActive(id.application(), id.type(), 1).get(Step.deployReal));
        assertEquals(List.of(entry3, entry4),
                logs.readActive(id.application(), id.type(), 2).get(Step.deployReal));
        assertEquals(List.of(entry4),
                logs.readActive(id.application(), id.type(), 3).get(Step.deployReal));
        assertEquals(RunLog.empty(), logs.readActive(id.application(), id.type(), 4));

        // We should now have three chunks, with two, two, and one entries.
        assertEquals(Optional.of(4L), buffer.readLastLogEntryId(id.application(), id.type()));
        assertArrayEquals(new long[]{0, 2, 4}, buffer.getLogChunkIds(id.application(), id.type()).toArray());

        // Flushing clears the buffer entirely, and stores its aggregated content in the data store.
        logs.flush(id);
        assertEquals(Optional.empty(), buffer.readLastLogEntryId(id.application(), id.type()));
        assertArrayEquals(new long[]{}, buffer.getLogChunkIds(id.application(), id.type()).toArray());
        assertEquals(RunLog.empty(), logs.readActive(id.application(), id.type(), -1));

        assertEquals(List.of(entry0, entry1, entry2, entry3, entry4),
                logs.readFinished(id, -1).get().get(Step.deployReal));
        assertEquals(List.of(entry1, entry2, entry3, entry4),
                logs.readFinished(id, 0).get().get(Step.deployReal));
        assertEquals(List.of(entry2, entry3, entry4),
                logs.readFinished(id, 1).get().get(Step.deployReal));
        assertEquals(List.of(entry3, entry4),
                logs.readFinished(id, 2).get().get(Step.deployReal));
        assertEquals(List.of(entry4),
                logs.readFinished(id, 3).get().get(Step.deployReal));
        assertEquals(List.of(), logs.readFinished(id, 4).get().get(Step.deployReal));

        // Logging a large entry enough times to reach the maximum size causes no further entries to be stored.
        List<LogEntry> monsterLog = IntStream.range(0, 2 * maxChunks + 3)
                .mapToObj(i -> new LogEntry(i, entry.at(), entry.type(), entry.message()))
                .collect(toUnmodifiableList());
        List<LogEntry> logged = new ArrayList<>(monsterLog);
        logged.remove(logged.size() - 1);
        logged.remove(logged.size() - 1);
        logged.remove(logged.size() - 1);
        logged.add(new LogEntry(2 * maxChunks, entry.at(), LogEntry.Type.warning, "Max log size of " + ((chunkSize * maxChunks) >> 20) + "Mb exceeded; further user entries are discarded."));

        logs.append(id.application(), id.type(), Step.deployReal, monsterLog, false);
        assertEquals(logged.size(),
                logs.readActive(id.application(), id.type(), -1).get(Step.deployReal).size());
        assertEquals(logged,
                logs.readActive(id.application(), id.type(), -1).get(Step.deployReal));

        // An additional, forced entry is appended.
        LogEntry forced = new LogEntry(logged.size(), entry.at(), entry.type(), entry.message());
        logs.append(id.application(), id.type(), Step.deployReal, List.of(forced), true);
        logged.add(forced);
        assertEquals(logged.size(),
                logs.readActive(id.application(), id.type(), -1).get(Step.deployReal).size());
        assertEquals(logged,
                logs.readActive(id.application(), id.type(), -1).get(Step.deployReal));
        logged.remove(logged.size() - 1);

        // Flushing the buffer clears it again, and makes it ready for reuse.
        logs.flush(id);
        for (int i = 0; i < 2 * maxChunks + 3; i++)
            logs.append(id.application(), id.type(), Step.deployReal, List.of(entry), false);
        assertEquals(logged.size(),
                logs.readActive(id.application(), id.type(), -1).get(Step.deployReal).size());
        assertEquals(logged,
                logs.readActive(id.application(), id.type(), -1).get(Step.deployReal));
    }

}
