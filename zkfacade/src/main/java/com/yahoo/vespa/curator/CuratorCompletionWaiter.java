// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.curator;

import com.yahoo.log.LogLevel;
import com.yahoo.path.Path;
import org.apache.curator.framework.CuratorFramework;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Implementation of a Barrier that handles the case where more than number of members can call synchronize. If
 * the number of members that synchronize exceed the expected number, the other members are immediately allowed
 * to pass through the barrier.
 *
 * @author Vegard Havdal
 * @author Ulf Lilleengen
 */
class CuratorCompletionWaiter implements Curator.CompletionWaiter {

    private static final java.util.logging.Logger log = java.util.logging.Logger.getLogger(CuratorCompletionWaiter.class.getName());
    private final CuratorFramework curator;
    private final String barrierPath;
    private final String myId;
    private final int memberQty;
    private final Clock clock;

    CuratorCompletionWaiter(int barrierMembers, CuratorFramework curator, String barrierPath, String myId, Clock clock) {
        this.memberQty = barrierMembers;
        this.myId = barrierPath + "/" + myId;
        this.curator = curator;
        this.barrierPath = barrierPath;
        this.clock = clock;
    }

    @Override
    public void awaitCompletion(Duration timeout) {
        List<String> respondents;
        try {
            log.log(LogLevel.DEBUG, "Synchronizing on barrier " + barrierPath);
            respondents = awaitInternal(timeout);
            log.log(LogLevel.DEBUG, "Done synchronizing on barrier " + barrierPath);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        if (respondents.size() < memberQty) {
            throw new CompletionTimeoutException("Timed out waiting for peer config servers to complete operation. " +
                                                         "Got response from " + respondents + ", but need response from " +
                                                         "at least " + memberQty + " server(s). " +
                                                         "Timeout passed as argument was " + timeout.toMillis() + " ms");
        }
    }

    private List<String> awaitInternal(Duration timeout) throws Exception {
        Instant endTime = clock.instant().plus(timeout);
        List<String> respondents;
        do {
            respondents = curator.getChildren().forPath(barrierPath);
            if (respondents.size() >= memberQty) {
                break;
            }
            Thread.sleep(100);
        } while (clock.instant().isBefore(endTime));
        return respondents;
    }


    @Override
    public void notifyCompletion() {
        try {
            notifyInternal();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void notifyInternal() throws Exception {
        curator.create().forPath(myId);
    }

    @Override
    public String toString() {
        return "'" + barrierPath + "', " + memberQty + " members";
    }

    public static Curator.CompletionWaiter create(CuratorFramework curator, Path barrierPath, int numMembers, String id) {
        return new CuratorCompletionWaiter(numMembers, curator, barrierPath.getAbsolute(), id, Clock.systemUTC());
    }

    public static Curator.CompletionWaiter createAndInitialize(Curator curator, Path parentPath, String waiterNode, int numMembers, String id) {
        Path waiterPath = parentPath.append(waiterNode);
        curator.delete(waiterPath);
        curator.createAtomically(parentPath, waiterPath);
        return new CuratorCompletionWaiter(numMembers, curator.framework(), waiterPath.getAbsolute(), id, Clock.systemUTC());
    }
}
