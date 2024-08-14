// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.maintenance;

import com.yahoo.vespa.config.server.ApplicationRepository;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.flags.BooleanFlag;
import com.yahoo.vespa.flags.Flags;

import java.time.Duration;
import java.util.logging.Level;

/**
 * Removes expired config sessions
 *
 * @author hmusum
 */
public class SessionsMaintainer extends ConfigServerMaintainer {

    private final BooleanFlag deleteRemoteAndLocalAtTheSameTime;

    SessionsMaintainer(ApplicationRepository applicationRepository, Curator curator, Duration interval) {
        super(applicationRepository, curator, applicationRepository.flagSource(), applicationRepository.clock(),
              interval, true, true);
        this.deleteRemoteAndLocalAtTheSameTime = Flags.DELETE_EXPIRED_CONFIG_SESSIONS_NEW_PROCEDURE.bindTo(applicationRepository.flagSource());
    }

    @Override
    protected double maintain() {
        return maintain(() -> {});
    }

    double maintain(Runnable runnable) {
        if (deleteRemoteAndLocalAtTheSameTime.value()) {
            applicationRepository.deleteExpiredSessions();
        } else {
            applicationRepository.deleteExpiredLocalSessions();

            runnable.run(); // Used for testing, e.g. sleeping for some time

            int deleted = applicationRepository.deleteExpiredRemoteSessions();
            log.log(Level.FINE, () -> "Deleted " + deleted + " expired remote sessions");
        }

        return 1.0;
    }

}
