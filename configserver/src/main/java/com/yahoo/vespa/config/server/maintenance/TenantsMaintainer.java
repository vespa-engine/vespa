// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.maintenance;

import com.yahoo.vespa.config.server.ApplicationRepository;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.flags.FlagSource;

import java.time.Clock;
import java.time.Duration;

/**
 * Removes unused tenants (has no applications and was created more than 7 days ago)
 *
 * @author hmusum
 */
public class TenantsMaintainer extends ConfigServerMaintainer {

    static final Duration defaultTtlForUnusedTenant = Duration.ofDays(7);

    private final Duration ttlForUnusedTenant;
    private final Clock clock;

    TenantsMaintainer(ApplicationRepository applicationRepository, Curator curator, FlagSource flagSource,
                      Duration interval, Clock clock) {
        super(applicationRepository, curator, flagSource, interval, interval);
        this.ttlForUnusedTenant = defaultTtlForUnusedTenant;
        this.clock = clock;
    }

    @Override
    protected boolean maintain() {
        applicationRepository.deleteUnusedTenants(ttlForUnusedTenant, clock.instant());
        return true;
    }

}
