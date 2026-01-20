// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.maintenance;

import com.yahoo.config.provision.TenantName;
import com.yahoo.vespa.config.server.ApplicationRepository;
import com.yahoo.vespa.curator.Curator;

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

    TenantsMaintainer(ApplicationRepository applicationRepository, Curator curator, Duration interval, Clock clock) {
        super(applicationRepository, curator, applicationRepository.flagSource(), applicationRepository.clock(), interval, true);
        this.ttlForUnusedTenant = defaultTtlForUnusedTenant;
        this.clock = clock;
    }

    @Override
    protected double maintain() {
        if ( ! applicationRepository.configserverConfig().hostedVespa()) return 1.0;

        log.log(Level.FINE, "Starting deletion of unused tenants");
        try {
            Set<TenantName> tenants = applicationRepository.deleteUnusedTenants(ttlForUnusedTenant, clock.instant());
            if (!tenants.isEmpty()) {
                log.log(Level.INFO, "Deleted tenants " + tenants);
            }
        } catch (StackOverflowError e) {
            log.log(Level.WARNING, "Stack overflow when deleting tenants", e);
            return 0.0;
        }
        return 1.0;
    }

}
