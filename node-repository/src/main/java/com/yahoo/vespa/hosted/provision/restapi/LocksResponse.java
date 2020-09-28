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
             ThreadLockStats.getThreadLockInfos(),
             ThreadLockStats.getLockInfoSamples());
    }

    /** For testing */
    LocksResponse(TreeMap<String, LockCounters> lockCountersByPath,
                  List<ThreadLockStats> threadLockStats,
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
            lockPathCursor.setLong("timeout-on-reentrancy-errors", lockCounters.timeoutOnReentrancyErrorCount());
        });

        Cursor threadsCursor = root.setArray("threads");
        for (var threadLockInfo : threadLockStats) {
            List<LockAttempt> lockAttempts = threadLockInfo.getLockAttempts();
            if (!lockAttempts.isEmpty()) {
                Cursor threadLockInfoCursor = threadsCursor.addObject();
                threadLockInfoCursor.setString("thread-name", threadLockInfo.getThreadName());

                Cursor lockInfosCursor = threadLockInfoCursor.setArray("active-locks");
                for (var lockInfo : lockAttempts) {
                    setLockInfo(lockInfosCursor.addObject(), lockInfo, false);
                }

                threadLockInfoCursor.setString("stack-trace", threadLockInfo.getStackTrace());
            }
        }

        Cursor historicSamplesCursor = root.setArray("historic-samples");
        historicSamples.forEach(lockInfo -> setLockInfo(historicSamplesCursor.addObject(), lockInfo, true));
    }

    @Override
    public void render(OutputStream stream) throws IOException {
        new JsonFormat(true).encode(stream, slime);
    }

    @Override
    public String getContentType() {
        return "application/json";
    }

    private void setLockInfo(Cursor lockInfoCursor, LockAttempt lockAttempt, boolean includeThreadInfo) {
        if (includeThreadInfo) {
            lockInfoCursor.setString("thread-name", lockAttempt.getThreadName());
        }
        lockInfoCursor.setString("lock-path", lockAttempt.getLockPath());
        lockInfoCursor.setString("invoke-acquire-time", toString(lockAttempt.getTimeAcquiredWasInvoked()));
        lockInfoCursor.setString("acquire-timeout", lockAttempt.getAcquireTimeout().toString());
        lockAttempt.getTimeLockWasAcquired().ifPresent(instant -> lockInfoCursor.setString("lock-acquired-time", toString(instant)));
        lockInfoCursor.setString("lock-state", lockAttempt.getLockState().name());
        lockAttempt.getTimeTerminalStateWasReached().ifPresent(instant -> lockInfoCursor.setString("terminal-state-time", toString(instant)));
        lockInfoCursor.setString("acquire-duration", lockAttempt.getDurationOfAcquire().toString());
        lockInfoCursor.setString("locked-duration", lockAttempt.getDurationWithLock().toString());
        lockInfoCursor.setString("total-duration", lockAttempt.getDuration().toString());
        if (includeThreadInfo) {
            lockAttempt.getStackTrace().ifPresent(stackTrace -> lockInfoCursor.setString("stack-trace", stackTrace));
        }
    }

    private static String toString(Instant time) {
        return Instant.ofEpochMilli(time.toEpochMilli()).toString();
    }
}
