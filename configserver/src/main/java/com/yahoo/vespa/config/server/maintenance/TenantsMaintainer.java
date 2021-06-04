// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.maintenance;

import com.yahoo.config.provision.TenantName;
import com.yahoo.vespa.config.server.ApplicationRepository;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.flags.FlagSource;

import java.time.Clock;
import java.time.Duration;
import java.util.Set;
import java.util.logging.Level;

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
        super(applicationRepository, curator, flagSource, applicationRepository.clock().instant(), interval);
        this.ttlForUnusedTenant = defaultTtlForUnusedTenant;
        this.clock = clock;
    }

    @Override
    protected boolean maintain() {
        if ( ! applicationRepository.configserverConfig().hostedVespa()) return true;

        Set<TenantName> tenants = applicationRepository.deleteUnusedTenants(ttlForUnusedTenant, clock.instant());
        if (tenants.size() > 0) log.log(Level.INFO, "Deleted tenants " + tenants);
        return true;
    }

}
