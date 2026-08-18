// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision.zone;

import com.yahoo.config.provision.CloudName;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.Zone;

/**
 * Information about the Zone we're in that may have been propagated through environment variables
 * and is therefore a subset of the information in Zone.
 *
 * @author bratseth
 */
public record ZoneInfo(CloudName cloud, SystemName system, Environment environment, RegionName region) {

    public String systemLocalValue() { return environment + "." + region; }

    public static ZoneInfo from(Zone zone) {
        return new ZoneInfo(zone.cloud().name(), zone.system(), zone.environment(), zone.region());
    }

}
