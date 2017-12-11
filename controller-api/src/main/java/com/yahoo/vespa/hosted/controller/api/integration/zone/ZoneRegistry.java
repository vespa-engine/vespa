// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.zone;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.ZoneId;

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
    List<ZoneId> zones();
    boolean hasZone(ZoneId zoneId);
    List<URI> getConfigServerUris(ZoneId zoneId);
    Optional<URI> getLogServerUri(ZoneId zoneId);
    Duration getDeploymentTimeToLive(ZoneId zoneId);
    RegionName getDefaultRegion(Environment environment);
    URI getMonitoringSystemUri(Environment environment, RegionName name, ApplicationId application);
    URI getDashboardUri();

}
