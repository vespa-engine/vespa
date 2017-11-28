// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.zone;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.Zone;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Provides information about zones in a hosted Vespa system.
 *
 * @author mpolden
 */
public interface ZoneRegistry {

    SystemName system();
    List<Zone> zones();
    Optional<Zone> getZone(Environment environment, RegionName region);
    List<URI> getConfigServerUris(Environment environment, RegionName region);
    Optional<URI> getLogServerUri(Environment environment, RegionName region);
    Optional<Duration> getDeploymentTimeToLive(Environment environment, RegionName region);
    Optional<RegionName> getDefaultRegion(Environment environment);
    URI getMonitoringSystemUri(Environment environment, RegionName name, ApplicationId application);
    URI getDashboardUri();

}
