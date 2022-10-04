// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.integration;

import com.yahoo.config.provision.CloudName;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.zone.RoutingMethod;
import com.yahoo.config.provision.zone.ZoneApi;
import com.yahoo.config.provision.zone.ZoneFilter;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.config.provision.zone.ZoneList;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * A ZoneList implementation which assumes all zones are controllerManaged.
 *
 * @author jonmv
 */
public class ZoneFilterMock implements ZoneList {

    private final List<ZoneApi> zones;
    private final Map<ZoneApi, RoutingMethod> zoneRoutingMethods;
    private final Set<ZoneApi> dynamicallyProvisioned;
    private final boolean negate;

    private ZoneFilterMock(List<ZoneApi> zones, Map<ZoneApi, RoutingMethod> zoneRoutingMethods, Set<ZoneApi> dynamicallyProvisioned, boolean negate) {
        this.zones = zones;
        this.zoneRoutingMethods = zoneRoutingMethods;
        this.dynamicallyProvisioned = dynamicallyProvisioned;
        this.negate = negate;
    }

    public static ZoneFilter from(Collection<? extends ZoneApi> zones, Map<ZoneApi, RoutingMethod> routingMethods, Set<ZoneApi> dynamicallyProvisioned) {
        return new ZoneFilterMock(List.copyOf(zones), Map.copyOf(routingMethods), dynamicallyProvisioned, false);
    }

    @Override
    public ZoneList not() {
        return new ZoneFilterMock(zones, zoneRoutingMethods, dynamicallyProvisioned, ! negate);
    }

    @Override
    public ZoneList all() {
        return filter(zone -> true);
    }

    @Override
    public ZoneList publiclyVisible() {
        return controllerUpgraded();
    }

    @Override
    public ZoneList controllerUpgraded() {
        return all();
    }

    @Override
    public ZoneList routingMethod(RoutingMethod method) {
        return filter(zone -> zoneRoutingMethods.get(zone) == method);
    }

    @Override
    public ZoneList reachable() {
        return all();
    }

    @Override
    public ZoneList dynamicallyProvisioned() {
        return filter(dynamicallyProvisioned::contains);
    }

    @Override
    public ZoneList in(Environment... environments) {
        return filter(zone -> Set.of(environments).contains(zone.getEnvironment()));
    }

    @Override
    public ZoneList in(RegionName... regions) {
        return filter(zone -> Set.of(regions).contains(zone.getRegionName()));
    }

    @Override
    public ZoneList in(CloudName... clouds) {
        return filter(zone -> Set.of(clouds).contains(zone.getCloudName()));
    }

    @Override
    public ZoneList among(ZoneId... zones) {
        return filter(zone -> Set.of(zones).contains(zone.getId()));
    }

    @Override
    public List<? extends ZoneApi> zones() {
        return List.copyOf(zones);
    }

    private ZoneFilterMock filter(Predicate<ZoneApi> condition) {
        return new ZoneFilterMock(
                zones.stream()
                        .filter(zone -> negate ?
                                condition.negate().test(zone) :
                                condition.test(zone))
                        .collect(Collectors.toList()),
                zoneRoutingMethods, dynamicallyProvisioned, false);
    }

}
