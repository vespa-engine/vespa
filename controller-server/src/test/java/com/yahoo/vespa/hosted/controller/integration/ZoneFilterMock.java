// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.integration;

import com.yahoo.config.provision.CloudName;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.zone.ZoneApi;
import com.yahoo.config.provision.zone.ZoneFilter;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.config.provision.zone.ZoneList;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * A ZoneList implementation which assumes all zones are controllerManaged.
 *
 * @author jonmv
 */
public class ZoneFilterMock implements ZoneList {

    private final List<ZoneApi> zones;
    private final boolean negate;

    private ZoneFilterMock(List<ZoneApi> zones, boolean negate) {
        this.zones = zones;
        this.negate = negate;
    }

    public static ZoneFilter from(Collection<ZoneApi> zones) {
        return new ZoneFilterMock(new ArrayList<>(zones), false);
    }

    @Override
    public ZoneList not() {
        return new ZoneFilterMock(zones, ! negate);
    }

    @Override
    public ZoneList all() {
        return filter(zone -> true);
    }

    @Override
    public ZoneList controllerUpgraded() {
        return all();
    }

    @Override
    public ZoneList directlyRouted() {
        return all();
    }

    @Override
    public ZoneList reachable() {
        return all();
    }

    @Override
    public ZoneList in(Environment... environments) {
        return filter(zone -> new HashSet<>(Arrays.asList(environments)).contains(zone.getEnvironment()));
    }

    @Override
    public ZoneList in(RegionName... regions) {
        return filter(zone -> new HashSet<>(Arrays.asList(regions)).contains(zone.getRegionName()));
    }

    @Override
    public ZoneList among(ZoneId... zones) {
        return filter(zone -> new HashSet<>(Arrays.asList(zones)).contains(zone.getId()));
    }

    @Override
    public List<? extends ZoneApi> zones() {
        return List.copyOf(zones);
    }

    @Override
    public List<ZoneId> ids() {
        return List.copyOf(zones.stream().map(ZoneApi::getId).collect(Collectors.toList()));
    }

    @Override
    public ZoneList ofCloud(CloudName cloud) {
        return filter(zone -> zone.getCloudName().equals(cloud));
    }

    private ZoneFilterMock filter(Predicate<ZoneApi> condition) {
        return new ZoneFilterMock(
                zones.stream()
                        .filter(zone -> negate ?
                                condition.negate().test(zone) :
                                condition.test(zone))
                        .collect(Collectors.toList()),
                false);
    }

}
