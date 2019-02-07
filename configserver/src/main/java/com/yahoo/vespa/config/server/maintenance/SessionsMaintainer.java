// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.maintenance;

import com.yahoo.vespa.config.server.ApplicationRepository;
import com.yahoo.vespa.curator.Curator;

import java.time.Duration;

/**
 * Removes inactive sessions
 * <p>
 * Note: Unit test is in ApplicationRepositoryTest
 *
 * @author hmusum
 */
public class SessionsMaintainer extends Maintainer {
    private final boolean hostedVespa;

    SessionsMaintainer(ApplicationRepository applicationRepository, Curator curator, Duration interval) {
        // Start this maintainer immediately. It frees disk space, so if disk goes full and config server
        // restarts this makes sure that cleanup will happen as early as possible
        super(applicationRepository, curator, Duration.ZERO, interval);
        this.hostedVespa = applicationRepository.configserverConfig().hostedVespa();
    }

    @Override
    protected void maintain() {
        applicationRepository.deleteExpiredLocalSessions();

        // Expired remote sessions are not expected to exist, they should have been deleted when
        // a deployment happened or when the application was deleted. We still see them from time to time,
        // probably due to some race or another bug
        if (hostedVespa) {
            Duration expiryTime = Duration.ofDays(30);
            applicationRepository.deleteExpiredRemoteSessions(expiryTime);
        }
    }
}
