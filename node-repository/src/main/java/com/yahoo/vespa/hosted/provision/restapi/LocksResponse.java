// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.restapi;

import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.JsonFormat;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.curator.stats.LockAttempt;
import com.yahoo.vespa.curator.stats.LockCounters;
import com.yahoo.vespa.curator.stats.ThreadLockStats;

import java.io.IOException;
import java.io.OutputStream;
import java.time.Instant;
import java.util.List;
import java.util.TreeMap;

/**
 * Returns information related to ZooKeeper locks.
 *
 * @author hakon
 */
public class LocksResponse extends HttpResponse {

    private final Slime slime = new Slime();

    public LocksResponse() {
        this(new TreeMap<>(ThreadLockStats.getLockCountersByPath()),
             ThreadLockStats.getThreadLockStats(),
             ThreadLockStats.getLockAttemptSamples());
    }

    /** For testing */
    LocksResponse(TreeMap<String, LockCounters> lockCountersByPath,
                  List<ThreadLockStats> threadLockStatsList,
                  List<LockAttempt> historicSamples) {
        super(200);

        Cursor root = slime.setObject();

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
            List<LockAttempt> lockAttempts = threadLockStats.getLockAttempts();
            if (!lockAttempts.isEmpty()) {
                Cursor threadLockStatsCursor = threadsCursor.addObject();
                threadLockStatsCursor.setString("thread-name", threadLockStats.getThreadName());

                Cursor lockAttemptsCursor = threadLockStatsCursor.setArray("active-locks");
                for (var lockAttempt : lockAttempts) {
                    setLockAttempt(lockAttemptsCursor.addObject(), lockAttempt, false);
                }

                threadLockStatsCursor.setString("stack-trace", threadLockStats.getStackTrace());
            }
        }

        Cursor historicSamplesCursor = root.setArray("historic-samples");
        historicSamples.forEach(lockAttempt -> setLockAttempt(historicSamplesCursor.addObject(), lockAttempt, true));
    }

    @Override
    public void render(OutputStream stream) throws IOException {
        new JsonFormat(true).encode(stream, slime);
    }

    @Override
    public String getContentType() {
        return "application/json";
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
    }

    private static String toString(Instant time) {
        return Instant.ofEpochMilli(time.toEpochMilli()).toString();
    }
}
