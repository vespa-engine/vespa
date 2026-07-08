// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.maintenance;

import com.yahoo.vespa.config.server.ApplicationRepository;
import com.yahoo.vespa.curator.Curator;

import java.time.Duration;


/**
 * Finds stale host registry entries and removes them
 *
 * @author hmusum
 */
public class HostRegistryMaintainer extends ConfigServerMaintainer {

    HostRegistryMaintainer(ApplicationRepository applicationRepository, Curator curator, Duration interval) {
        super(applicationRepository, curator, applicationRepository.flagSource(), applicationRepository.clock(), interval, false);
    }

    @Override
    protected double maintain() {
        applicationRepository.removeStaleHostRegistryEntries();
        return 1.0;
    }

}
