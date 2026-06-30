// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.curator;

import com.yahoo.path.Path;
import com.yahoo.vespa.curator.transaction.CuratorOperations;
import com.yahoo.vespa.curator.transaction.CuratorTransaction;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.logging.Level;

import static com.yahoo.vespa.curator.Curator.CompletionWaiter;

/**
 * Implementation of a Barrier that handles the case where more than number of members can call synchronize.
 * Will wait for some time for all servers to do the operation, but will accept the majority of servers to have
 * done the operation if it takes longer than a specified amount of time.
 *
 * @author Vegard Havdal
 * @author Ulf Lilleengen
 */
class CuratorCompletionWaiter implements CompletionWaiter {

    private static final java.util.logging.Logger log = java.util.logging.Logger.getLogger(CuratorCompletionWaiter.class.getName());

    private final Curator curator;
    private final Path barrierPath;
    private final Path waiterNode;
    private final Clock clock;
    private final Duration waitForAll;

    CuratorCompletionWaiter(Curator curator, Path barrierPath, String serverId, Clock clock, Duration waitForAll) {
        this.waiterNode = barrierPath.append(serverId);
        this.curator = curator;
        this.barrierPath = barrierPath;
        this.clock = clock;
        this.waitForAll = waitForAll;
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
        Instant gotQuorumTime = Instant.EPOCH;

        List<String> respondents;
        do {
            respondents = curator.framework().getChildren().forPath(barrierPath.getAbsolute());
            if (log.isLoggable(Level.FINER)) {
                log.log(Level.FINER, respondents.size() + "/" + curator.zooKeeperEnsembleCount() + " responded: " +
                                     respondents + ", all participants: " + curator.zooKeeperEnsembleConnectionSpec());
            }

            // If all config servers responded, return
            if (respondents.size() == curator.zooKeeperEnsembleCount()) {
                logBarrierCompleted(respondents, startTime);
                break;
            }
            // If some are missing, quorum is enough, but wait for all up to ´waitForAll´ seconds before returning
            if (respondents.size() >= barrierMemberCount()) {
                if (gotQuorumTime.isBefore(startTime))
                    gotQuorumTime = clock.instant();

                if (Duration.between(clock.instant(), gotQuorumTime.plus(waitForAll)).isNegative()) {
                    logBarrierCompleted(respondents, startTime);
                    break;
                }
            }

            Thread.sleep(100);
        } while (clock.instant().isBefore(endTime));

        return respondents;
    }

    private void logBarrierCompleted(List<String> respondents, Instant startTime) {
        Duration duration = Duration.between(startTime, Instant.now());
        Level level = duration.minus(Duration.ofSeconds(5)).isNegative() ? Level.FINE : Level.INFO;
        log.log(level, () -> barrierCompletedMessage(respondents, duration));
    }

    private String barrierCompletedMessage(List<String> respondents, Duration duration) {
        return barrierPath + " completed in " + duration.toString() +
                ", " + respondents.size() + "/" + curator.zooKeeperEnsembleCount() + " responded: " + respondents;
    }

    @Override
    public void notifyCompletion() {
        try {
            curator.framework().create().forPath(waiterNode.getAbsolute());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String toString() {
        return "'" + barrierPath + "', " + barrierMemberCount() + " members";
    }

    public static CompletionWaiter create(Curator curator, Path barrierPath, String id, Duration waitForAll) {
        return new CuratorCompletionWaiter(curator, barrierPath, id, Clock.systemUTC(), waitForAll);
    }

    public static CompletionWaiter createAndInitialize(Curator curator, Path barrierPath, String id, Duration waitForAll) {
        // Note: Should be done atomically, but unable to that when path may not exist before delete
        // and create should be able to create any missing parent paths
        curator.delete(barrierPath);
        curator.create(barrierPath);

        return new CuratorCompletionWaiter(curator, barrierPath, id, Clock.systemUTC(), waitForAll);
    }

    private int barrierMemberCount() {
        return (curator.zooKeeperEnsembleCount() / 2) + 1; // majority
    }

}
