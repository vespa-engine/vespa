// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model;

import com.yahoo.config.provision.CloudName;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.model.container.configserver.option.ConfigOptions;

/**
 * Information about the Zone we're in that has been propagated through environment variables.
 *
 * @author bratseth
 */
public record ZoneInfo(CloudName cloud, SystemName system, Environment environment, RegionName region) {

    public String systemLocalValue() { return environment + "." + region; }

    public static ZoneInfo from(ConfigOptions options) {
        return new ZoneInfo(options.cloud().map(CloudName::from).orElse(CloudName.DEFAULT),
                            options.system().map(SystemName::from).orElse(SystemName.defaultSystem()),
                            options.environment().map(Environment::from).orElse(Environment.defaultEnvironment()),
                            options.region().map(RegionName::from).orElse(RegionName.defaultName()));
    }

    public static ZoneInfo from(Zone zone) {
        return new ZoneInfo(zone.cloud().name(), zone.system(), zone.environment(), zone.region());
    }

}
