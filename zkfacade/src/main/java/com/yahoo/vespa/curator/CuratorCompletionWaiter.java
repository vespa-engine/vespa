// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.curator;

import com.yahoo.path.Path;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

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
    private final Curator curator;
    private final String barrierPath;
    private final String myId;
    private final Clock clock;

    CuratorCompletionWaiter(Curator curator, String barrierPath, String myId, Clock clock) {
        this.myId = barrierPath + "/" + myId;
        this.curator = curator;
        this.barrierPath = barrierPath;
        this.clock = clock;
    }

    @Override
    public void awaitCompletion(Duration timeout) {
        List<String> respondents;
        try {
            log.log(Level.FINE, () -> "Synchronizing on barrier " + barrierPath);
            respondents = awaitInternal(timeout);
            log.log(Level.FINE, () -> "Done synchronizing on barrier " + barrierPath);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        if (respondents.size() < barrierMemberCount()) {
            throw new CompletionTimeoutException("Timed out waiting for config servers to complete operation " +
                                                 "(waited for barrier " + barrierPath + ")." +
                                                 "Got response from " + respondents + ", but need response from " +
                                                 "at least " + barrierMemberCount() + " server(s). " +
                                                 "Timeout passed as argument was " + timeout.toMillis() + " ms");
        }
    }

    private List<String> awaitInternal(Duration timeout) throws Exception {
        Instant startTime = clock.instant();
        Instant endTime = startTime.plus(timeout);

        List<String> respondents = new ArrayList<>();
        do {
            respondents.clear();
            respondents.addAll(curator.framework().getChildren().forPath(barrierPath));
            if (log.isLoggable(Level.FINER)) {
                log.log(Level.FINER, respondents.size() + "/" + curator.zooKeeperEnsembleCount() + " responded: " +
                                     respondents + ", all participants: " + curator.zooKeeperEnsembleConnectionSpec());
            }

            // First, check if all config servers responded
            if (respondents.size() == curator.zooKeeperEnsembleCount()) {
                log.log(Level.FINE, () -> barrierCompletedMessage(respondents, startTime));
                break;
            }
            // If some are missing, quorum is enough
            if (respondents.size() >= barrierMemberCount()) {
                log.log(Level.FINE, () -> barrierCompletedMessage(respondents, startTime));
                break;
            }

            Thread.sleep(100);
        } while (clock.instant().isBefore(endTime));

        return respondents;
    }

    private String barrierCompletedMessage(List<String> respondents, Instant startTime) {
        return barrierPath + " completed in " + Duration.between(startTime, Instant.now()).toString() +
               ", " + respondents.size() + "/" + curator.zooKeeperEnsembleCount() + " responded: " + respondents;
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
        curator.framework().create().forPath(myId);
    }

    @Override
    public String toString() {
        return "'" + barrierPath + "', " + barrierMemberCount() + " members";
    }

    public static Curator.CompletionWaiter create(Curator curator, Path barrierPath, String id) {
        return new CuratorCompletionWaiter(curator, barrierPath.getAbsolute(), id, Clock.systemUTC());
    }

    public static Curator.CompletionWaiter createAndInitialize(Curator curator, Path parentPath, String waiterNode, String id) {
        Path waiterPath = parentPath.append(waiterNode);

        String debugMessage = log.isLoggable(Level.FINE) ? "Recreating ZK path " + waiterPath : null;
        if (debugMessage != null) log.fine(debugMessage);

        curator.delete(waiterPath);
        curator.createAtomically(waiterPath);

        if (debugMessage != null) log.fine(debugMessage + ": Done");

        return new CuratorCompletionWaiter(curator, waiterPath.getAbsolute(), id, Clock.systemUTC());
    }

    private int barrierMemberCount() {
        return (curator.zooKeeperEnsembleCount() / 2) + 1; // majority
    }

}
