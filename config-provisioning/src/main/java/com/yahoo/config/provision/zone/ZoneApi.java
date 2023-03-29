// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision.zone;

import com.yahoo.config.provision.CloudName;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.SystemName;

/**
 * @author hakonhall
 */
public interface ZoneApi {

    SystemName getSystemName();

    /**
     * Returns the ID of the zone.
     *
     * WARNING: The ID of a controller zone is equal to the ID of a prod zone in the same region.
     * @see #getVirtualId()
     */
    ZoneId getId();

    /** Returns the SYSTEM.ENVIRONMENT.REGION string. */
    default String getFullName() {
        return getSystemName().value() + "." + getEnvironment().value() + "." + getRegionName().value();
    }

    /**
     * Returns {@link #getId()} for all zones except the controller zone.  Unlike {@link #getId()},
     * the virtual ID of a controller is distinct from all other zones.
     */
    default ZoneId getVirtualId() {
        return getId();
    }

    default Environment getEnvironment() { return getId().environment(); }

    default RegionName getRegionName() { return getId().region(); }

    CloudName getCloudName();

    /** Returns the region name within the cloud, e.g. 'us-east-1' in AWS */
    String getCloudNativeRegionName();

    /** Returns the availability zone within the cloud, e.g. 'use1-az2' in AWS */
    default String getCloudNativeAvailabilityZone() { throw new UnsupportedOperationException(); }

}
