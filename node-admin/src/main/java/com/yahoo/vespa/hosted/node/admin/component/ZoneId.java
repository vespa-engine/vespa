// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.component;

import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.SystemName;

import java.util.Objects;

/**
 * @author freva
 */
public class ZoneId {
    private final SystemName systemName;
    private final Environment environment;
    private final RegionName regionName;

    public ZoneId(SystemName systemName, Environment environment, RegionName regionName) {
        this.systemName = systemName;
        this.environment = environment;
        this.regionName = regionName;
    }

    public SystemName systemName() {
        return systemName;
    }

    public Environment environment() {
        return environment;
    }

    public RegionName regionName() {
        return regionName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ZoneId zoneId = (ZoneId) o;
        return systemName == zoneId.systemName &&
                environment == zoneId.environment &&
                Objects.equals(regionName, zoneId.regionName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(systemName, environment, regionName);
    }
}
