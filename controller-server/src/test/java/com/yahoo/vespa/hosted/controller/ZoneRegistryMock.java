// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller;

import com.google.inject.Inject;
import com.yahoo.component.AbstractComponent;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.Zone;
import com.yahoo.config.provision.ZoneId;
import com.yahoo.vespa.hosted.controller.api.integration.zone.ZoneRegistry;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * @author mpolden
 */
public class ZoneRegistryMock extends AbstractComponent implements ZoneRegistry {

    private final Map<ZoneId, Duration> deploymentTimeToLive = new HashMap<>();
    private final Map<Environment, RegionName> defaultRegionForEnvironment = new HashMap<>();
    private List<ZoneId> zones = new ArrayList<>();
    private SystemName system = SystemName.main;

    @Inject
    public ZoneRegistryMock() {
        this.zones.add(new Zone(SystemName.main, Environment.from("prod"), RegionName.from("corp-us-east-1")));
        this.zones.add(new Zone(SystemName.main, Environment.from("prod"), RegionName.from("us-east-3")));
        this.zones.add(new Zone(SystemName.main, Environment.from("prod"), RegionName.from("us-west-1")));
    }

    public ZoneRegistryMock setDeploymentTimeToLive(ZoneId zone, Duration duration) {
        deploymentTimeToLive.put(zone, duration);
        return this;
    }

    public ZoneRegistryMock setDefaultRegionForEnvironment(Environment environment, RegionName region) {
        defaultRegionForEnvironment.put(environment, region);
        return this;
    }

    public ZoneRegistryMock setZones(List<ZoneId> zones) {
        this.zones = zones;
        return this;
    }

    public ZoneRegistryMock setSystem(SystemName system) {
        this.system = system;
        return this;
    }

    @Override
    public SystemName system() {
        return system;
    }

    @Override
    public List<ZoneId> zones() {
        return Collections.unmodifiableList(zones);
    }

    @Override
    public Optional<ZoneId> getZone(Environment environment, RegionName region) {
        return zones().stream().filter(z -> z.environment().equals(environment) &&
                                            z.region().equals(region)).findFirst();
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
    public Optional<RegionName> getDefaultRegion(Environment environment) {
        return Optional.ofNullable(defaultRegionForEnvironment.get(environment));
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
