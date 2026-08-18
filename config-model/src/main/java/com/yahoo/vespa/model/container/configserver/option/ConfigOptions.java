// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.configserver.option;

import com.yahoo.config.provision.CloudName;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.Zone;
import com.yahoo.config.provision.zone.ZoneInfo;

import java.time.Duration;
import java.util.Optional;

/**
 * @author Tony Vaagenes
 */
public interface ConfigOptions {

    class ConfigServer {
        public final String hostName;
        public final Optional<Integer> port;

        public ConfigServer(String hostName, Optional<Integer> port) {
            this.hostName = hostName;
            this.port = port;
        }
    }

    Optional<Integer> rpcPort();
    Optional<Boolean> multiTenant();
    Optional<Boolean> hostedVespa();
    ConfigServer[] allConfigServers();
    int[] configServerZookeeperIds();
    Optional<Duration> zookeeperBarrierTimeout();
    Optional<Duration> applicationLockTimeoutSeconds();
    Optional<String> environment();
    Optional<String> region();
    Optional<String> system();
    Optional<String> cloud();
    Optional<Boolean> useVespaVersionInRequest();
    String zooKeeperSnapshotMethod();
    Integer zookeeperJuteMaxBuffer(); // in bytes

    default ZoneInfo toZoneInfo() {
        if (!hostedVespa().orElse(false)) return ZoneInfo.from(Zone.defaultZone());

        return new ZoneInfo(cloud().map(CloudName::from).orElse(CloudName.DEFAULT),
                            system().map(SystemName::from).orElse(SystemName.defaultSystem()),
                            environment().map(Environment::from).orElse(Environment.defaultEnvironment()),
                            region().map(RegionName::from).orElse(RegionName.defaultName()));
    }



}
