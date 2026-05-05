// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision.zone;

import com.yahoo.config.provision.CloudName;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.SystemName;

/**
 * @author hakonhall
 */
public interface ZoneApi {

    SystemName systemName();

    /** Returns a unique ID across all config server zones (including the controller zone) within the system. */
    default ZoneId id() { return legacyId(); }

    CloudName cloudName();

    /** Returns the region name within the cloud, e.g. 'us-east-1' in AWS */
    String cloudNativeRegionName();

    /** Returns the availability zone within the cloud, e.g. 'use1-az2' in AWS */
    default String getCloudNativeAvailabilityZone() { throw new UnsupportedOperationException(); }

    /**
     * Returns the legacy ID of the zone.  It is "legacy" because a controller and prod config server gets the same ID.</p>
     *
     * @see #id()
     */
    ZoneId legacyId();

    /** Returns the SYSTEM.ENVIRONMENT.REGION string. WARNING: Uses {@link #legacyId()} by default. */
    default String fullName() {
        return systemName().value() + "." + getEnvironment().value() + "." + getRegionName().value();
    }

    /** WARNING: Uses {@link #legacyId()} by default. */
    default Environment getEnvironment() { return legacyId().environment(); }

    /** WARNING: Uses {@link #legacyId()} by default. */
    default RegionName getRegionName() { return legacyId().region(); }

}
