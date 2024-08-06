// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.maintenance;

import com.yahoo.vespa.config.server.ApplicationRepository;
import com.yahoo.vespa.curator.Curator;

import java.time.Duration;
import java.util.logging.Level;

/**
 * Removes expired sessions
 * <p>
 * Note: Unit test is in ApplicationRepositoryTest
 *
 * @author hmusum
 */
public class SessionsMaintainer extends ConfigServerMaintainer {

    SessionsMaintainer(ApplicationRepository applicationRepository, Curator curator, Duration interval) {
        super(applicationRepository, curator, applicationRepository.flagSource(), applicationRepository.clock(),
              interval, true, true);
    }

    @Override
    protected double maintain() {
        return maintain(Duration.ZERO);
    }

    // For testing, simulate delay when deleting many sessions
    double maintain(Duration delay) {
        applicationRepository.deleteExpiredLocalSessions();

        // Delay
        try {
            Thread.sleep(delay.toMillis());
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        int deleted = applicationRepository.deleteExpiredRemoteSessions();
        log.log(Level.FINE, () -> "Deleted " + deleted + " expired remote sessions");

        return 1.0;
    }

}
