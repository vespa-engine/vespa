// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.zone;

import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.vespa.athenz.api.AthenzService;
import com.yahoo.vespa.hosted.controller.api.identifiers.DeploymentId;

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

    /** Returns whether the system of this registry contains the given zone. */
    boolean hasZone(ZoneId zoneId);

    /** Returns a list containing the id of all zones in this registry. */
    ZoneFilter zones();

    /** Returns the default region for the given environment, if one is configured. */
    Optional<RegionName> getDefaultRegion(Environment environment);

    // TODO Rename to getConfigServerUris once port 4080 is removed from configservers
    /** Returns a list with all known config servers in the given zone, with a secure connection URL. */
    List<URI> getConfigServerSecureUris(ZoneId zoneId);

    /** Returns a URL with the logs for the given deployment, if logging is configured for its zone. */
    Optional<URI> getLogServerUri(DeploymentId deploymentId);

    /** Returns the time to live for deployments in the given zone, or empty if this is infinite. */
    Optional<Duration> getDeploymentTimeToLive(ZoneId zoneId);

    /** Returns a URL pointing at monitoring resources for the given deployment. */
    URI getMonitoringSystemUri(DeploymentId deploymentId);

    /** Returns the URL of the dashboard for the system of this registry. */
    URI getDashboardUri();

    /** Returns the system of this registry. */
    SystemName system();

    /** Return the configserver's Athenz service identity */
    AthenzService getConfigserverAthenzService(ZoneId zoneId);

}
