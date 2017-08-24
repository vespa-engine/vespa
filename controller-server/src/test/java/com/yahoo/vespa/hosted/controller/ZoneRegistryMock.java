// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.hosted.controller.api.integration.zone.ZoneRegistry;

import java.net.URI;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * @author mpolden
 */
public class ZoneRegistryMock implements ZoneRegistry {

    private final Map<Zone, Duration> deploymentTimeToLive = new HashMap<>();

    public void setDeploymentTimeToLive(Zone zone, Duration duration) {
        deploymentTimeToLive.put(zone, duration);
    }

    @Override
    public SystemName system() {
        return SystemName.main;
    }

    @Override
    public List<Zone> zones() {
        return Collections.singletonList(new Zone(SystemName.main, Environment.from("prod"), RegionName.from("corp-us-east-1")));
    }

    @Override
    public Optional<Zone> getZone(Environment environment, RegionName region) {
        return zones().stream().filter(z -> z.environment().equals(environment) && z.region().equals(region)).findFirst();
    }

    @Override
    public List<URI> getConfigServerUris(Environment environment, RegionName region) {
        return getZone(environment, region)
                .map(z -> URI.create(String.format("http://cfg.%s.%s.test", environment.value(), region.value())))
                .map(Collections::singletonList)
                .orElse(Collections.emptyList());
    }

    @Override
    public Optional<URI> getLogServerUri(Environment environment, RegionName region) {
        return getZone(environment, region)
                .map(z -> URI.create(String.format("http://log.%s.%s.test", environment.value(), region.value())));
    }

    @Override
    public Optional<Duration> getDeploymentTimeToLive(Environment environment, RegionName region) {
        return Optional.ofNullable(deploymentTimeToLive.get(new Zone(environment, region)));
    }

    @Override
    public URI getMonitoringSystemUri(Environment environment, RegionName name, ApplicationId application) {
        return URI.create("http://monitoring-system.test/?environment=" + environment.value() + "&region="
                                  + name.value() + "&application=" + application.toShortString());
    }

    @Override
    public URI getDashboardUri() {
        return URI.create("http://dashboard.test");
    }
}
