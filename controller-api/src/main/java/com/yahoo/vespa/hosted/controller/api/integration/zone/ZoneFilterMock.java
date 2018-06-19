// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.zone;

import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.RegionName;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * A Zones.List implementation which assumes all zones are controllerManaged.
 *
 * @author jvenstad
 */
public class ZoneFilterMock implements ZoneList {

    private final java.util.List<ZoneId> zones;
    private final boolean negate;

    private ZoneFilterMock(java.util.List<ZoneId> zones, boolean negate) {
        this.negate = negate;
        this.zones = zones;
    }

    public static ZoneFilter from(Collection<ZoneId> zones) {
        return new ZoneFilterMock(new ArrayList<>(zones), false);
    }

    @Override
    public ZoneList not() {
        return new ZoneFilterMock(zones, ! negate);
    }

    @Override
    public ZoneList all() {
        return filter(zoneId -> true);
    }

    @Override
    public ZoneList controllerUpgraded() {
        return all();
    }

    @Override
    public ZoneList reachable() {
        return all();
    }

    @Override
    public ZoneList in(Environment... environments) {
        return filter(zoneId -> new HashSet<>(Arrays.asList(environments)).contains(zoneId.environment()));
    }

    @Override
    public ZoneList in(RegionName... regions) {
        return filter(zoneId -> new HashSet<>(Arrays.asList(regions)).contains(zoneId.region()));
    }

    @Override
    public ZoneList among(ZoneId... zones) {
        return filter(zoneId -> new HashSet<>(Arrays.asList(zones)).contains(zoneId));
    }

    @Override
    public java.util.List<ZoneId> ids() {
        return Collections.unmodifiableList(zones);
    }

    private ZoneFilterMock filter(Predicate<ZoneId> condition) {
        return new ZoneFilterMock(zones.stream().filter(negate ? condition.negate() : condition).collect(Collectors.toList()), false);
    }

}
