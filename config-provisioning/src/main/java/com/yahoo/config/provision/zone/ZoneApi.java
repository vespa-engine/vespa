// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision.zone;

import com.yahoo.config.provision.AzName;
import com.yahoo.config.provision.CloudName;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.SystemName;

/**
 * @author hakonhall
 */
public interface ZoneApi {

    SystemName systemName();
    CloudName cloudName();

    /** Returns a unique ID across all config server zones (including the controller zone) within the system. */
    default ZoneId id() { return legacyId(); }

    /** Returns the region name within the cloud, e.g. 'us-east-1' in AWS */
    String cloudNativeRegionName();

    /** Returns the availability zone within the cloud, e.g. 'use1-az2' in AWS */
    default AzName cloudNativeAvailabilityZone() { throw new UnsupportedOperationException(); }

    /**
     * Returns the legacy ID of the zone.  It is "legacy" because a controller and prod config server gets the same ID.</p>
     *
     * @see #id()
     */
    ZoneId legacyId();

    /** Returns the SYSTEM.ENVIRONMENT.REGION string. WARNING: The default implementation uses {@link #legacyId()}. */
    default String legacyFullName() {
        return systemName().value() + "." + legacyEnvironment().value() + "." + legacyRegionName().value();
    }

    /** WARNING: The default implementation uses {@link #legacyId()}. */
    default Environment legacyEnvironment() { return legacyId().environment(); }

    /** WARNING: The default implementation uses {@link #legacyId()}. */
    default RegionName legacyRegionName() { return legacyId().region(); }

}
