// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller;

import com.google.inject.Inject;
import com.yahoo.component.AbstractComponent;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.ZoneId;
import com.yahoo.vespa.hosted.controller.api.identifiers.DeploymentId;
import com.yahoo.vespa.hosted.controller.api.integration.zone.ZoneRegistry;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
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
        this.zones.add(ZoneId.from("prod", "corp-us-east-1"));
        this.zones.add(ZoneId.from("prod", "us-east-3"));
        this.zones.add(ZoneId.from("prod", "us-west-1"));
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
    public boolean hasZone(ZoneId zoneId) {
        return zones.contains(zoneId);
    }

    @Override
    public List<URI> getConfigServerUris(ZoneId zoneId) {
        return Collections.singletonList(URI.create(String.format("http://cfg.%s.test", zoneId.value())));
    }

    @Override
    public List<URI> getConfigServerSecureUris(ZoneId zoneId) {
        return Collections.singletonList(URI.create(String.format("https://cfg.%s.test:4443", zoneId.value())));
    }

    @Override
    public Optional<URI> getLogServerUri(DeploymentId deploymentId) {
        if ( ! hasZone(deploymentId.zoneId()))
            return Optional.empty();

        String kibanaQuery = "/#/discover?_g=()&_a=(columns:!(_source)," +
                             "index:'logstash-*',interval:auto," +
                             "query:(query_string:(analyze_wildcard:!t,query:'" +
                             "HV-tenant:%22" + deploymentId.applicationId().tenant().value() + "%22%20" +
                             "AND%20HV-application:%22" + deploymentId.applicationId().application().value() + "%22%20" +
                             "AND%20HV-region:%22" + deploymentId.zoneId().region().value() + "%22%20" +
                             "AND%20HV-instance:%22" + deploymentId.applicationId().instance().value() + "%22%20" +
                             "AND%20HV-environment:%22" + deploymentId.zoneId().environment().value() + "%22'))," +
                             "sort:!('@timestamp',desc))";

        URI kibanaPath = URI.create(kibanaQuery);
        return Optional.of(URI.create(String.format("http://log.%s.test", deploymentId.zoneId().value())).resolve(kibanaPath));
    }

    @Override
    public Duration getDeploymentTimeToLive(ZoneId zoneId) {
        return deploymentTimeToLive.containsKey(zoneId)
                ? deploymentTimeToLive.get(zoneId)
                : Duration.ofMillis(Long.MAX_VALUE / 2);
    }

    @Override
    public Optional<RegionName> getDefaultRegion(Environment environment) {
        return Optional.ofNullable(defaultRegionForEnvironment.get(environment));
    }

    @Override
    public URI getMonitoringSystemUri(DeploymentId deploymentId) {
        return URI.create("http://monitoring-system.test/?environment=" + deploymentId.zoneId().environment().value() + "&region="
                          + deploymentId.zoneId().region().value() + "&application=" + deploymentId.applicationId().toShortString());
    }

    @Override
    public URI getDashboardUri() {
        return URI.create("http://dashboard.test");
    }

}
