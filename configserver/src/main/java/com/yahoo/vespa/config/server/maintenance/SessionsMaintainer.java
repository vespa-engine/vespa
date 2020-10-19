// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.maintenance;

import com.yahoo.vespa.config.server.ApplicationRepository;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.flags.FlagSource;

import java.time.Duration;

/**
 * Removes expired sessions and locks
 * <p>
 * Note: Unit test is in ApplicationRepositoryTest
 *
 * @author hmusum
 */
public class SessionsMaintainer extends ConfigServerMaintainer {

    SessionsMaintainer(ApplicationRepository applicationRepository, Curator curator, Duration interval, FlagSource flagSource) {
        super(applicationRepository, curator, flagSource, Duration.ofMinutes(1), interval);
    }

    @Override
    protected boolean maintain() {
        applicationRepository.deleteExpiredSessions();
        return true;
    }

}
