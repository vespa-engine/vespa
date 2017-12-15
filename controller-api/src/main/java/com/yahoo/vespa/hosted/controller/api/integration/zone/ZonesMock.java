package com.yahoo.vespa.hosted.controller.api.integration.zone;

import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.RegionName;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * A Zones.List implementation which assumes all zones are controllerManaged.
 *
 * @author jvenstad
 */
public class ZonesMock implements Zones.List {

    private final java.util.List<ZoneId> zones;
    private final boolean negate;

    private ZonesMock(java.util.List<ZoneId> zones, boolean negate) {
        this.negate = negate;
        this.zones = zones;
    }

    public static Zones from(Collection<ZoneId> zones) {
        return new ZonesMock(new ArrayList<>(zones), false);
    }

    @Override
    public Zones.List not() {
        return new ZonesMock(zones, ! negate);
    }

    @Override
    public Zones.List all() {
        return filter(zoneId -> true);
    }

    @Override
    public Zones.List controllerManaged() {
        return all();
    }

    @Override
    public Zones.List in(Environment environment) {
        return filter(zoneId -> zoneId.environment() == environment);
    }

    @Override
    public Zones.List in(RegionName region) {
        return filter(zoneId -> zoneId.region().equals(region));
    }

    @Override
    public java.util.List<ZoneId> ids() {
        return Collections.unmodifiableList(zones);
    }

    private ZonesMock filter(Predicate<ZoneId> condition) {
        return new ZonesMock(zones.stream().filter(negate ? condition.negate() : condition).collect(Collectors.toList()), false);
    }

}
