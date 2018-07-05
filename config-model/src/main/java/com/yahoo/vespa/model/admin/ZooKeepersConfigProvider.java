// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.admin;

import com.yahoo.collections.CollectionUtil;
import com.yahoo.cloud.config.ZookeepersConfig;
import com.yahoo.config.model.api.ConfigServerSpec;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Tony Vaagenes
 */
public class ZooKeepersConfigProvider implements ZookeepersConfig.Producer {

    public static final int zkPort = Integer.getInteger("zk_port", 2181);

    private final List<Configserver> configServers;

    public ZooKeepersConfigProvider(List<Configserver> configServers) {
        if (configServers == null) {
            configServers = new ArrayList<>();
        }
        this.configServers = configServers;
    }

    // format for each element: hostname:port
    public List<String> getZooKeepers() {
        List<String> servers = new ArrayList<>();
        for (Configserver server : configServers) {
            ConfigServerSpec serverSpec = server.getConfigServerSpec();
            servers.add(serverSpec.getHostName() + ":" + serverSpec.getZooKeeperPort());

        }
        return servers;
    }

    // format: hostname1:port2,hostname2:port2,...
    public String getZooKeepersConnectionSpec() {
        return CollectionUtil.mkString(getZooKeepers(), ",");
    }

    @Override
    public void getConfig(ZookeepersConfig.Builder builder) {
        builder.zookeeperserverlist(getZooKeepersConnectionSpec());
    }
}
