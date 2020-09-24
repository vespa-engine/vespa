// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.restapi;

import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.JsonFormat;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.curator.LockInfo;
import com.yahoo.vespa.curator.ThreadLockInfo;

import java.io.IOException;
import java.io.OutputStream;
import java.time.Instant;
import java.util.List;

public class LocksResponse extends HttpResponse {

    private final Slime slime;

    public LocksResponse() {
        super(200);

        this.slime = new Slime();
        Cursor root = slime.setObject();

        root.setLong("in-critical-region", ThreadLockInfo.inCriticalRegionCount());
        root.setLong("invoke-acquire", ThreadLockInfo.invokeAcquireCount());
        root.setLong("acquire-timed-out", ThreadLockInfo.acquireTimedOutCount());
        root.setLong("lock-acquired", ThreadLockInfo.lockAcquiredCount());
        root.setLong("locks-released", ThreadLockInfo.locksReleasedCount());
        root.setLong("acquire-reentrant-lock-errors", ThreadLockInfo.failedToAcquireReentrantLockCount());
        root.setLong("no-locks-errors", ThreadLockInfo.noLocksErrorCount());
        root.setLong("timeout-on-reentrancy-errors", ThreadLockInfo.timeoutOnReentrancyErrorCount());

        List<ThreadLockInfo> threadLockInfos = ThreadLockInfo.getThreadLockInfos();
        if (!threadLockInfos.isEmpty()) {
            Cursor threadsCursor = root.setArray("threads");
            threadLockInfos.forEach(threadLockInfo -> {
                Cursor threadLockInfoCursor = threadsCursor.addObject();
                threadLockInfoCursor.setString("thread-name", threadLockInfo.getThreadName());
                threadLockInfoCursor.setString("lock-path", threadLockInfo.getLockPath());

                List<LockInfo> lockInfos = threadLockInfo.getLockInfos();
                if (!lockInfos.isEmpty()) {
                    Cursor lockInfosCursor = threadLockInfoCursor.setArray("locks");
                    lockInfos.forEach(lockInfo -> {
                        Cursor lockInfoCursor = lockInfosCursor.addObject();
                        lockInfoCursor.setString("invoke-acquire-time", toString(lockInfo.getTimeAcquiredWasInvoked()));
                        lockInfoCursor.setLong("reentrancy-hold-count-on-acquire", lockInfo.getThreadHoldCountOnAcquire());
                        lockInfoCursor.setString("acquire-timeout", lockInfo.getAcquireTimeout().toString());
                        lockInfo.getTimeLockWasAcquired().ifPresent(instant -> lockInfoCursor.setString("lock-acquired-time", toString(instant)));
                        lockInfoCursor.setString("lock-state", lockInfo.getLockState().name());
                        lockInfo.getTimeTerminalStateWasReached().ifPresent(instant -> lockInfoCursor.setString("terminal-state-time", toString(instant)));
                    });
                }
            });
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

    private static String toString(Instant time) {
        return Instant.ofEpochMilli(time.toEpochMilli()).toString();
    }
}
