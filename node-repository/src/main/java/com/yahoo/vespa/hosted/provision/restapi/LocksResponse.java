// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.restapi;

import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.JsonFormat;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.curator.stats.LockCounters;
import com.yahoo.vespa.curator.stats.LockInfo;
import com.yahoo.vespa.curator.stats.ThreadLockInfo;

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
        this(new TreeMap<>(ThreadLockInfo.getLockCountersByPath()),
             ThreadLockInfo.getThreadLockInfos(),
             ThreadLockInfo.getSlowLockInfos());
    }

    /** For testing */
    LocksResponse(TreeMap<String, LockCounters> lockCountersByPath,
                  List<ThreadLockInfo> threadLockInfos,
                  List<LockInfo> slowLockInfos) {
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
        int numberOfStackTraces = 0;
        for (var threadLockInfo : threadLockInfos) {
            List<LockInfo> lockInfos = threadLockInfo.getLockInfos();
            if (!lockInfos.isEmpty()) {
                Cursor threadLockInfoCursor = threadsCursor.addObject();
                threadLockInfoCursor.setString("thread-name", threadLockInfo.getThreadName());
                threadLockInfoCursor.setString("lock-path", threadLockInfo.getLockPath());

                Cursor lockInfosCursor = threadLockInfoCursor.setArray("active-locks");
                for (var lockInfo : lockInfos) {
                    if (numberOfStackTraces++ < 10) {  // Expensive to generate stack traces?
                        lockInfo.fillStackTrace();
                    }
                    setLockInfo(lockInfosCursor.addObject(), lockInfo, false);
                }
            }
        }

        Cursor slowLocksCursor = root.setArray("slow-locks");
        slowLockInfos.forEach(lockInfo -> setLockInfo(slowLocksCursor.addObject(), lockInfo, true));
    }

    @Override
    public void render(OutputStream stream) throws IOException {
        new JsonFormat(true).encode(stream, slime);
    }

    @Override
    public String getContentType() {
        return "application/json";
    }

    private void setLockInfo(Cursor lockInfoCursor, LockInfo lockInfo, boolean includeThreadInfo) {
        if (includeThreadInfo) {
            lockInfoCursor.setString("thread-name", lockInfo.getThreadName());
            lockInfoCursor.setString("lock-path", lockInfo.getLockPath());
        }
        lockInfoCursor.setString("invoke-acquire-time", toString(lockInfo.getTimeAcquiredWasInvoked()));
        lockInfoCursor.setString("acquire-timeout", lockInfo.getAcquireTimeout().toString());
        lockInfo.getTimeLockWasAcquired().ifPresent(instant -> lockInfoCursor.setString("lock-acquired-time", toString(instant)));
        lockInfoCursor.setString("lock-state", lockInfo.getLockState().name());
        lockInfo.getTimeTerminalStateWasReached().ifPresent(instant -> lockInfoCursor.setString("terminal-state-time", toString(instant)));
        lockInfoCursor.setString("acquire-duration", lockInfo.getDurationOfAcquire().toString());
        lockInfoCursor.setString("locked-duration", lockInfo.getDurationWithLock().toString());
        lockInfoCursor.setString("total-duration", lockInfo.getDuration().toString());
        lockInfo.getStackTrace().ifPresent(stackTrace -> lockInfoCursor.setString("stack-trace", stackTrace));
    }

    private static String toString(Instant time) {
        return Instant.ofEpochMilli(time.toEpochMilli()).toString();
    }
}
