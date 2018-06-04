// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.maintenance;

import com.yahoo.vespa.config.server.ApplicationRepository;
import com.yahoo.vespa.curator.Curator;

import java.time.Duration;
import java.time.Instant;

public class TenantsMaintainer extends Maintainer {

    private final Duration ttlForUnusedTenant;

    TenantsMaintainer(ApplicationRepository applicationRepository, Curator curator, Duration interval) {
        this(applicationRepository, curator, interval, Duration.ofDays(7));
    }

    private TenantsMaintainer(ApplicationRepository applicationRepository, Curator curator, Duration interval, Duration ttlForUnusedTenant) {
        super(applicationRepository, curator, interval);
        this.ttlForUnusedTenant = ttlForUnusedTenant;
    }

    @Override
    // Delete unused tenants that were created more than ttlForUnusedTenant ago
    protected void maintain() {
        applicationRepository.deleteUnusedTenants(ttlForUnusedTenant, Instant.now());
    }
}
