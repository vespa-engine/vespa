// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.persistence;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.hosted.controller.api.integration.LogEntry;
import com.yahoo.vespa.hosted.controller.api.integration.RunDataStore;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.RunId;
import com.yahoo.vespa.hosted.controller.api.integration.stubs.MockRunDataStore;
import com.yahoo.vespa.hosted.controller.deployment.RunLog;
import com.yahoo.vespa.hosted.controller.deployment.Step;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class BufferedLogStoreTest {

    @Test
    public void chunkingAndFlush() {
        CuratorDb buffer = new MockCuratorDb();
        RunDataStore store = new MockRunDataStore();
        BufferedLogStore logs = new BufferedLogStore(buffer, store);
        RunId id = new RunId(ApplicationId.from("tenant", "application", "instance"),
                             JobType.productionUsWest1,
                             123);

        byte[] manyBytes = new byte[BufferedLogStore.chunkSize / 2 + 1]; // One fits, and two (over-)fills.
        Arrays.fill(manyBytes, (byte) 'O');
        LogEntry entry = new LogEntry(0, 123, LogEntry.Type.warning, new String(manyBytes));

        // Log entries are re-sequenced by the log store, by enumeration.
        LogEntry entry0 = new LogEntry(0, entry.at(), entry.type(), entry.message());
        LogEntry entry1 = new LogEntry(1, entry.at(), entry.type(), entry.message());
        LogEntry entry2 = new LogEntry(2, entry.at(), entry.type(), entry.message());

        assertEquals(Optional.empty(), logs.readFinished(id, -1));
        assertEquals(RunLog.empty(), logs.readActive(id.application(), id.type(), -1));

        logs.append(id.application(), id.type(), Step.deployReal, Collections.singletonList(entry));
        assertEquals(List.of(entry0),
                     logs.readActive(id.application(), id.type(), -1).get(Step.deployReal));
        assertEquals(RunLog.empty(), logs.readActive(id.application(), id.type(), 0));

        logs.append(id.application(), id.type(), Step.deployReal, Collections.singletonList(entry));
        assertEquals(List.of(entry0, entry1),
                     logs.readActive(id.application(), id.type(), -1).get(Step.deployReal));
        assertEquals(List.of(entry1),
                     logs.readActive(id.application(), id.type(), 0).get(Step.deployReal));
        assertEquals(RunLog.empty(), logs.readActive(id.application(), id.type(), 1));

        logs.append(id.application(), id.type(), Step.deployReal, Collections.singletonList(entry));
        assertEquals(List.of(entry0, entry1, entry2),
                     logs.readActive(id.application(), id.type(), -1).get(Step.deployReal));
        assertEquals(List.of(entry1, entry2),
                     logs.readActive(id.application(), id.type(), 0).get(Step.deployReal));
        assertEquals(List.of(entry2),
                     logs.readActive(id.application(), id.type(), 1).get(Step.deployReal));
        assertEquals(RunLog.empty(), logs.readActive(id.application(), id.type(), 2));

        // We should now have two chunks, with two and one entries.
        assertEquals(Optional.of(2L), buffer.readLastLogEntryId(id.application(), id.type()));
        assertArrayEquals(new long[]{0, 2}, buffer.getLogChunkIds(id.application(), id.type()).toArray());

        // Flushing clears the buffer entirely, and stores its aggregated content in the data store.
        logs.flush(id);
        assertEquals(Optional.empty(), buffer.readLastLogEntryId(id.application(), id.type()));
        assertArrayEquals(new long[]{}, buffer.getLogChunkIds(id.application(), id.type()).toArray());
        assertEquals(RunLog.empty(), logs.readActive(id.application(), id.type(), -1));

        assertEquals(List.of(entry0, entry1, entry2),
                     logs.readFinished(id, -1).get().get(Step.deployReal));
        assertEquals(List.of(entry1, entry2),
                     logs.readFinished(id, 0).get().get(Step.deployReal));
        assertEquals(List.of(entry2),
                     logs.readFinished(id, 1).get().get(Step.deployReal));
        assertEquals(Collections.emptyList(), logs.readFinished(id, 2).get().get(Step.deployReal));
    }

}
