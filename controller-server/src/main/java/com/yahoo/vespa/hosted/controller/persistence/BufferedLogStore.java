// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.persistence;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.hosted.controller.api.integration.LogEntry;
import com.yahoo.vespa.hosted.controller.api.integration.RunDataStore;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.RunId;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.TestReport;
import com.yahoo.vespa.hosted.controller.deployment.RunLog;
import com.yahoo.vespa.hosted.controller.deployment.Step;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Stores logs in bite-sized chunks using a {@link CuratorDb}, and flushes to a
 * {@link com.yahoo.vespa.hosted.controller.api.integration.RunDataStore} when the log is final.
 *
 * @author jonmv
 */
public class BufferedLogStore {

    private static final int defaultChunkSize = 1 << 13; // 8kb per node stored in ZK.
    private static final int defaultMaxLogSize = 1 << 26; // 64Mb max per run.

    private final int chunkSize;
    private final int maxLogSize;
    private final CuratorDb buffer;
    private final RunDataStore store;
    private final LogSerializer logSerializer = new LogSerializer();

    public BufferedLogStore(CuratorDb buffer, RunDataStore store) {
        this(defaultChunkSize, defaultMaxLogSize, buffer, store);
    }

    BufferedLogStore(int chunkSize, int maxLogSize, CuratorDb buffer, RunDataStore store) {
        this.chunkSize = chunkSize;
        this.maxLogSize = maxLogSize;
        this.buffer = buffer;
        this.store = store;
    }

    /** Appends to the log of the given, active run, reassigning IDs as counted here, and converting to Vespa log levels. */
    public void append(ApplicationId id, JobType type, Step step, List<LogEntry> entries, boolean forceLog) {
        if (entries.isEmpty())
            return;

        // Start a new chunk if the previous one is full, or if none have been written yet.
        // The id of a chunk is the id of the first entry in it.
        long lastEntryId = buffer.readLastLogEntryId(id, type).orElse(-1L);
        long lastChunkId = buffer.getLogChunkIds(id, type).max().orElse(0);
        long numberOfChunks = Math.max(1, buffer.getLogChunkIds(id, type).count());
        if (numberOfChunks > maxLogSize / chunkSize && ! forceLog)
            return; // Max size exceeded — store no more.

        byte[] emptyChunk = "[]".getBytes();
        byte[] lastChunk = buffer.readLog(id, type, lastChunkId).orElse(emptyChunk);

        long sizeLowerBound = lastChunk.length;
        Map<Step, List<LogEntry>> log = logSerializer.fromJson(lastChunk, -1);
        List<LogEntry> stepEntries = log.computeIfAbsent(step, __ -> new ArrayList<>());
        for (LogEntry entry : entries) {
            if (sizeLowerBound > chunkSize) {
                buffer.writeLog(id, type, lastChunkId, logSerializer.toJson(log));
                buffer.writeLastLogEntryId(id, type, lastEntryId);
                lastChunkId = lastEntryId + 1;
                if (++numberOfChunks > maxLogSize / chunkSize && ! forceLog) {
                    log = Map.of(step, List.of(new LogEntry(++lastEntryId,
                                                            entry.at(),
                                                            LogEntry.Type.warning,
                                                            "Max log size of " + (maxLogSize >> 20) +
                                                            "Mb exceeded; further user entries are discarded.")));
                    break;
                }
                log = new HashMap<>();
                log.put(step, stepEntries = new ArrayList<>());
                sizeLowerBound = 2;
            }
            stepEntries.add(new LogEntry(++lastEntryId, entry.at(), entry.type(), entry.message()));
            sizeLowerBound += entry.message().length();
        }
        buffer.writeLog(id, type, lastChunkId, logSerializer.toJson(log));
        buffer.writeLastLogEntryId(id, type, lastEntryId);
    }

    /** Reads all log entries after the given threshold, from the buffered log, i.e., for an active run. */
    public RunLog readActive(ApplicationId id, JobType type, long after) {
        return buffer.readLastLogEntryId(id, type).orElse(-1L) <= after
                ? RunLog.empty()
                : RunLog.of(readChunked(id, type, after));
    }

    /** Reads all log entries after the given threshold, from the stored log, i.e., for a finished run. */
    public Optional<RunLog> readFinished(RunId id, long after) {
        return store.get(id).map(json -> RunLog.of(logSerializer.fromJson(json, after)));
    }

    /** Writes the buffered log of the now finished run to the long-term store, and clears the buffer. */
    public void flush(RunId id) {
        store.put(id, logSerializer.toJson(readChunked(id.application(), id.type(), -1)));
        buffer.deleteLog(id.application(), id.type());
    }

    /** Deletes the logs for the given run, if already moved to storage. */
    public void delete(RunId id) {
        store.delete(id);
    }

    /** Deletes all logs in permanent storage for the given application. */
    public void delete(ApplicationId id) {
        store.delete(id);
    }

    private Map<Step, List<LogEntry>> readChunked(ApplicationId id, JobType type, long after) {
        long[] chunkIds = buffer.getLogChunkIds(id, type).toArray();
        int firstChunk = chunkIds.length;
        while (firstChunk > 0 && chunkIds[--firstChunk] > after + 1);
        return logSerializer.fromJson(Arrays.stream(chunkIds, firstChunk, chunkIds.length)
                                            .mapToObj(chunkId -> buffer.readLog(id, type, chunkId))
                                            .flatMap(Optional::stream)
                                            .collect(Collectors.toList()),
                                      after);
    }

    public Optional<String> readTestReports(RunId id) {
        return store.getTestReport(id).map(bytes -> "[" + new String(bytes, UTF_8) + "]");
    }

    public void writeTestReport(RunId id, TestReport report) {
        byte[] bytes = report.toJson().getBytes(UTF_8);
        Optional<byte[]> existing = store.getTestReport(id);
        if (existing.isPresent()) {
            byte[] aggregate = new byte[existing.get().length + 1 + bytes.length];
            System.arraycopy(existing.get(), 0, aggregate, 0, existing.get().length);
            aggregate[existing.get().length] = ',';
            System.arraycopy(bytes, 0, aggregate, existing.get().length + 1, bytes.length);
            bytes = aggregate;
        }
        store.putTestReport(id, bytes);
    }

}
