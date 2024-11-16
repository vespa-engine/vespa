// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.maintenance;

import com.yahoo.vespa.config.server.ApplicationRepository;
import com.yahoo.vespa.curator.Curator;

import java.time.Duration;

/**
 * Removes expired config sessions
 *
 * @author hmusum
 */
public class SessionsMaintainer extends ConfigServerMaintainer {

    private final int maxSessionsToDelete;

    SessionsMaintainer(ApplicationRepository applicationRepository, Curator curator, Duration interval) {
        super(applicationRepository, curator, applicationRepository.flagSource(), applicationRepository.clock(),
              interval, true, true);
        this.maxSessionsToDelete = 50;
    }

    SessionsMaintainer(ApplicationRepository applicationRepository, Curator curator, Duration interval, int maxSessionsToDelete) {
        super(applicationRepository, curator, applicationRepository.flagSource(), applicationRepository.clock(),
                interval, true, true);
        this.maxSessionsToDelete = maxSessionsToDelete;
    }

    @Override
    protected double maintain() {
        applicationRepository.deleteExpiredSessions(maxSessionsToDelete);

        return 1.0;
    }

}
