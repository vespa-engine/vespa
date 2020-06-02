// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision.zone;

import com.yahoo.config.provision.Cloud;
import com.yahoo.config.provision.CloudName;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.SystemName;

/**
 * @author hakonhall
 */
public interface ZoneApi {
    SystemName getSystemName();

    ZoneId getId();
    default Environment getEnvironment() { return getId().environment(); }
    default RegionName getRegionName() { return getId().region(); }

    CloudName getCloudName();
    Cloud getCloud();

    /** Returns the region name within the cloud, e.g. 'us-east-1' in AWS */
    String getCloudNativeRegionName();
}
