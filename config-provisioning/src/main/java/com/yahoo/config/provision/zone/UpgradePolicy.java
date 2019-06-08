// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision.zone;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * This class declares the order to use when upgrading zones in a system.
 *
 * @author mpolden
 */
public class UpgradePolicy {

    private final List<List<ZoneId>> zones;

    private UpgradePolicy(List<List<ZoneId>> zones) {
        this.zones = zones;
    }

    public List<List<ZoneId>> asList() {
        return List.copyOf(zones);
    }

    private UpgradePolicy with(ZoneId... zone) {
        List<List<ZoneId>> zones = new ArrayList<>(this.zones);
        zones.add(Arrays.asList(zone));
        return new UpgradePolicy(zones);
    }

    /** Upgrade given zone as the next step */
    public UpgradePolicy upgrade(ZoneApi zone) {
        return with(zone.toDeprecatedId());
    }

    /** Upgrade given zones in parallel as the next step */
    public UpgradePolicy upgradeInParallel(ZoneApi... zone) {
        return with(Stream.of(zone).map(ZoneApi::toDeprecatedId).toArray(ZoneId[]::new));
    }

    public static UpgradePolicy create() {
        return new UpgradePolicy(Collections.emptyList());
    }

}
