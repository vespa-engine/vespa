// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.restapi;

import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.net.HostName;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.JsonFormat;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.curator.stats.LockStats;
import com.yahoo.vespa.curator.stats.LockAttempt;
import com.yahoo.vespa.curator.stats.LockCounters;
import com.yahoo.vespa.curator.stats.RecordedLockAttempts;
import com.yahoo.vespa.curator.stats.ThreadLockStats;

import java.io.IOException;
import java.io.OutputStream;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Returns information related to ZooKeeper locks.
 *
 * @author hakon
 */
public class LocksResponse extends HttpResponse {

    private final Slime slime = new Slime();

    public LocksResponse() {
        this(HostName.getLocalhost(),
             new TreeMap<>(LockStats.getGlobal().getLockCountersByPath()),
             LockStats.getGlobal().getThreadLockStats(),
             LockStats.getGlobal().getLockAttemptSamples());
    }

    /** For testing */
    LocksResponse(String hostname,
                  TreeMap<String, LockCounters> lockCountersByPath,
                  List<ThreadLockStats> threadLockStatsList,
                  List<LockAttempt> historicSamples) {
        super(200);

        Cursor root = slime.setObject();

        root.setString("hostname", hostname);

        Cursor lockPathsCursor = root.setArray("lock-paths");
        lockCountersByPath.forEach((lockPath, lockCounters) -> {
            Cursor lockPathCursor = lockPathsCursor.addObject();
            lockPathCursor.setString("path", lockPath);
            lockPathCursor.setLong("in-critical-region", lockCounters.inCriticalRegionCount());
            lockPathCursor.setLong("invoke-acquire", lockCounters.invokeAcquireCount());
            lockPathCursor.setLong("acquire-failed", lockCounters.acquireFailedCount());
            lockPathCursor.setLong("acquire-timed-out", lockCounters.acquireTimedOutCount());
            lockPathCursor.setLong("lock-acquired", lockCounters.lockAcquiredCount());
            lockPathCursor.setLong("locks-released", lockCounters.locksReleasedCount());
            lockPathCursor.setLong("no-locks-errors", lockCounters.noLocksErrorCount());
            lockPathCursor.setLong("lock-release-errors", lockCounters.lockReleaseErrorCount());
        });

        Cursor threadsCursor = root.setArray("threads");
        for (var threadLockStats : threadLockStatsList) {
            Optional<LockAttempt> ongoingLockAttempt = threadLockStats.getTopMostOngoingLockAttempt();
            Optional<RecordedLockAttempts> ongoingRecording = threadLockStats.getOngoingRecording();
            if (ongoingLockAttempt.isEmpty() && ongoingRecording.isEmpty()) {
                continue;
            }

            Cursor threadCursor = threadsCursor.addObject();
            threadCursor.setString("thread-name", threadLockStats.getThreadName());

            ongoingLockAttempt.ifPresent(lockAttempt -> {
                setLockAttempt(threadCursor.setObject("active-lock"), lockAttempt, false);
                threadsCursor.setString("stack-trace", threadLockStats.getStackTrace());
            });

            ongoingRecording.ifPresent(recording -> setRecording(threadCursor.setObject("ongoing-recording"), recording));
        }

        Cursor historicSamplesCursor = root.setArray("slow-locks");
        historicSamples.stream()
                .sorted(Comparator.comparing(LockAttempt::getDuration).reversed())
                .forEach(lockAttempt -> setLockAttempt(historicSamplesCursor.addObject(), lockAttempt, true));

        List<RecordedLockAttempts> historicRecordings = LockStats.getGlobal().getHistoricRecordings().stream()
                .sorted(Comparator.comparing(RecordedLockAttempts::duration).reversed())
                .collect(Collectors.toList());
        if (!historicRecordings.isEmpty()) {
            Cursor recordingsCursor = root.setArray("recordings");
            historicRecordings.forEach(recording -> setRecording(recordingsCursor.addObject(), recording));
        }
    }

    @Override
    public void render(OutputStream stream) throws IOException {
        new JsonFormat(true).encode(stream, slime);
    }

    @Override
    public String getContentType() {
        return "application/json";
    }

    private void setRecording(Cursor cursor, RecordedLockAttempts recording) {
        cursor.setString("record-id", recording.recordId());
        cursor.setString("start-time", toString(recording.startInstant()));
        cursor.setString("duration", recording.duration().toString());
        Cursor locksCursor = cursor.setArray("locks");
        recording.lockAttempts().forEach(lockAttempt -> setLockAttempt(locksCursor.addObject(), lockAttempt, false));
    }

    private void setLockAttempt(Cursor lockAttemptCursor, LockAttempt lockAttempt, boolean includeThreadInfo) {
        if (includeThreadInfo) {
            lockAttemptCursor.setString("thread-name", lockAttempt.getThreadName());
        }
        lockAttemptCursor.setString("lock-path", lockAttempt.getLockPath());
        lockAttemptCursor.setString("invoke-acquire-time", toString(lockAttempt.getTimeAcquiredWasInvoked()));
        lockAttemptCursor.setString("acquire-timeout", lockAttempt.getAcquireTimeout().toString());
        lockAttempt.getTimeLockWasAcquired().ifPresent(instant -> lockAttemptCursor.setString("lock-acquired-time", toString(instant)));
        lockAttemptCursor.setString("lock-state", lockAttempt.getLockState().name());
        lockAttempt.getTimeTerminalStateWasReached().ifPresent(instant -> lockAttemptCursor.setString("terminal-state-time", toString(instant)));
        lockAttemptCursor.setString("acquire-duration", lockAttempt.getDurationOfAcquire().toString());
        lockAttemptCursor.setString("locked-duration", lockAttempt.getDurationWithLock().toString());
        lockAttemptCursor.setString("total-duration", lockAttempt.getDuration().toString());
        if (includeThreadInfo) {
            lockAttempt.getStackTrace().ifPresent(stackTrace -> lockAttemptCursor.setString("stack-trace", stackTrace));
        }

        List<LockAttempt> nestedLockAttempts = lockAttempt.getNestedLockAttempts();
        if (!nestedLockAttempts.isEmpty()) {
            Cursor nestedLockAttemptsCursor = lockAttemptCursor.setArray("nested-locks");
            nestedLockAttempts.forEach(nestedLockAttempt ->
                    setLockAttempt(nestedLockAttemptsCursor.addObject(), nestedLockAttempt, includeThreadInfo));
        }
    }

    private static String toString(Instant time) {
        return Instant.ofEpochMilli(time.toEpochMilli()).toString();
    }
}
