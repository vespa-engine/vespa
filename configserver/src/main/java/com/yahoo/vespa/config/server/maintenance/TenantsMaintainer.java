// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.maintenance;

import com.yahoo.vespa.config.server.ApplicationRepository;
import com.yahoo.vespa.curator.Curator;

import java.time.Duration;

public class TenantsMaintainer extends Maintainer {

    public TenantsMaintainer(ApplicationRepository applicationRepository, Curator curator, Duration interval) {
        super(applicationRepository, curator, interval);
    }

    @Override
    protected void maintain() {
        applicationRepository.removeUnusedTenants();
    }
}
