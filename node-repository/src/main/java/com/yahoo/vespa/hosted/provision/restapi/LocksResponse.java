// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.restapi;

import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.net.HostName;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.JsonFormat;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.curator.stats.LatencyMetrics;
import com.yahoo.vespa.curator.stats.LockMetrics;
import com.yahoo.vespa.curator.stats.LockStats;
import com.yahoo.vespa.curator.stats.LockAttempt;
import com.yahoo.vespa.curator.stats.RecordedLockAttempts;
import com.yahoo.vespa.curator.stats.ThreadLockStats;

import java.io.IOException;
import java.io.OutputStream;
import java.time.Duration;
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
             new TreeMap<>(LockStats.getGlobal().getLockMetricsByPath()),
             LockStats.getGlobal().getThreadLockStats(),
             LockStats.getGlobal().getLockAttemptSamples());
    }

    /** For testing */
    LocksResponse(String hostname,
                  TreeMap<String, LockMetrics> lockMetricsByPath,
                  List<ThreadLockStats> threadLockStatsList,
                  List<LockAttempt> historicSamples) {
        super(200);

        Cursor root = slime.setObject();

        root.setString("hostname", hostname);
        root.setString("time", Instant.now().toString());

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
                setLockAttempt(threadCursor.setObject("active-lock"), lockAttempt, false, false);
                threadCursor.setString("stack-trace", threadLockStats.getStackTrace());
            });

            ongoingRecording.ifPresent(recording -> setRecording(threadCursor.setObject("ongoing-recording"), recording));
        }

        Cursor historicSamplesCursor = root.setArray("slow-locks");
        historicSamples.stream()
                .sorted(Comparator.comparing(LockAttempt::getDuration).reversed())
                .forEach(lockAttempt -> setLockAttempt(historicSamplesCursor.addObject(), lockAttempt, true, true));

        List<RecordedLockAttempts> historicRecordings = LockStats.getGlobal().getHistoricRecordings().stream()
                .sorted(Comparator.comparing(RecordedLockAttempts::duration).reversed())
                .collect(Collectors.toList());
        if (!historicRecordings.isEmpty()) {
            Cursor recordingsCursor = root.setArray("recordings");
            historicRecordings.forEach(recording -> setRecording(recordingsCursor.addObject(), recording));
        }

        Cursor lockPathsCursor = root.setArray("lock-paths");
        lockMetricsByPath.forEach((lockPath, lockMetrics) -> {
            Cursor lockPathCursor = lockPathsCursor.addObject();
            lockPathCursor.setString("path", lockPath);
            setNonZeroLong(lockPathCursor, "acquireCount", lockMetrics.getCumulativeAcquireCount());
            setNonZeroLong(lockPathCursor, "acquireFailedCount", lockMetrics.getCumulativeAcquireFailedCount());
            setNonZeroLong(lockPathCursor, "acquireTimedOutCount", lockMetrics.getCumulativeAcquireTimedOutCount());
            setNonZeroLong(lockPathCursor, "lockedCount", lockMetrics.getCumulativeAcquireSucceededCount());
            setNonZeroLong(lockPathCursor, "releaseCount", lockMetrics.getCumulativeReleaseCount());
            setNonZeroLong(lockPathCursor, "releaseFailedCount", lockMetrics.getCumulativeReleaseFailedCount());
            setNonZeroLong(lockPathCursor, "reentryCount", lockMetrics.getCumulativeReentryCount());
            setNonZeroLong(lockPathCursor, "deadlock", lockMetrics.getCumulativeDeadlockCount());
            setNonZeroLong(lockPathCursor, "nakedRelease", lockMetrics.getCumulativeNakedReleaseCount());
            setNonZeroLong(lockPathCursor, "acquireWithoutRelease", lockMetrics.getCumulativeAcquireWithoutReleaseCount());
            setNonZeroLong(lockPathCursor, "foreignRelease", lockMetrics.getCumulativeForeignReleaseCount());

            setLatency(lockPathCursor, "acquire", lockMetrics.getAcquireLatencyMetrics());
            setLatency(lockPathCursor, "locked", lockMetrics.getLockedLatencyMetrics());
        });
    }

    private static void setNonZeroLong(Cursor cursor, String fieldName, long value) {
        if (value != 0) {
            cursor.setLong(fieldName, value);
        }
    }

    private static void setLatency(Cursor cursor, String name, LatencyMetrics latencyMetrics) {
        setNonZeroDouble(cursor, name + "Latency", latencyMetrics.latencySeconds());
        setNonZeroDouble(cursor, name + "MaxActiveLatency", latencyMetrics.maxActiveLatencySeconds());
        setNonZeroDouble(cursor, name + "Hz", latencyMetrics.endHz());
        setNonZeroDouble(cursor, name + "Load", latencyMetrics.load());
    }

    private static void setNonZeroDouble(Cursor cursor, String fieldName, double value) {
        if (Double.compare(value, 0.0) != 0) {
            cursor.setDouble(fieldName, value);
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
        recording.lockAttempts().forEach(lockAttempt -> setLockAttempt(locksCursor.addObject(), lockAttempt, false, false));
    }

    private void setLockAttempt(Cursor lockAttemptCursor, LockAttempt lockAttempt, boolean includeThreadName,
                                boolean includeStackTrace) {
        if (includeThreadName) {
            lockAttemptCursor.setString("thread-name", lockAttempt.getThreadName());
        }
        lockAttemptCursor.setString("lock-path", lockAttempt.getLockPath());
        lockAttemptCursor.setString("invoke-acquire-time", toString(lockAttempt.getTimeAcquiredWasInvoked()));
        lockAttemptCursor.setString("acquire-timeout", lockAttempt.getAcquireTimeout().toString());
        lockAttempt.getTimeLockWasAcquired().ifPresent(instant -> lockAttemptCursor.setString("lock-acquired-time", toString(instant)));
        lockAttemptCursor.setString("lock-state", lockAttempt.getLockState().name());
        lockAttempt.getTimeTerminalStateWasReached().ifPresent(instant -> lockAttemptCursor.setString("terminal-state-time", toString(instant)));
        lockAttemptCursor.setString("acquire-duration", toString(lockAttempt.getDurationOfAcquire()));
        lockAttemptCursor.setString("locked-duration", toString(lockAttempt.getDurationWithLock()));
        lockAttemptCursor.setString("total-duration", toString(lockAttempt.getDuration()));
        if (includeStackTrace) {
            lockAttempt.getStackTrace().ifPresent(stackTrace -> lockAttemptCursor.setString("stack-trace", stackTrace));
        }

        List<LockAttempt> nestedLockAttempts = lockAttempt.getNestedLockAttempts();
        if (!nestedLockAttempts.isEmpty()) {
            Cursor nestedLockAttemptsCursor = lockAttemptCursor.setArray("nested-locks");
            nestedLockAttempts.forEach(nestedLockAttempt ->
                    setLockAttempt(nestedLockAttemptsCursor.addObject(), nestedLockAttempt, false, includeStackTrace));
        }
    }

    private static String toString(Duration duration) {
        return Duration.ofMillis(duration.toMillis()).toString();
    }

    private static String toString(Instant time) {
        return Instant.ofEpochMilli(time.toEpochMilli()).toString();
    }
}
