// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.monitor.application;

import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.config.model.api.ApplicationInfo;
import com.yahoo.config.model.api.HostInfo;
import com.yahoo.config.model.api.PortInfo;
import com.yahoo.config.model.api.ServiceInfo;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.NodeType;
import com.yahoo.vespa.applicationmodel.ApplicationInstanceId;
import com.yahoo.vespa.applicationmodel.ClusterId;
import com.yahoo.vespa.applicationmodel.ConfigId;
import com.yahoo.vespa.applicationmodel.ServiceType;
import com.yahoo.vespa.applicationmodel.TenantId;
import com.yahoo.vespa.service.monitor.internal.ModelGenerator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A service/application model of the config server with health status.
 */
public class ConfigServerApplication extends HostedVespaApplication {

    public static final ConfigServerApplication CONFIG_SERVER_APPLICATION = new ConfigServerApplication();
    public static final TenantId TENANT_ID = new TenantId(CONFIG_SERVER_APPLICATION.getApplicationId().tenant().value());
    public static final ApplicationInstanceId APPLICATION_INSTANCE_ID =
            new ApplicationInstanceId(CONFIG_SERVER_APPLICATION.getApplicationId().application().value());
    public static final ClusterId CLUSTER_ID = new ClusterId(CONFIG_SERVER_APPLICATION.getClusterId().value());
    public static final ServiceType SERVICE_TYPE = new ServiceType("configserver");
    public static final String CONFIG_ID_PREFIX = "configid.";

    public static ConfigId configIdFrom(int index) {
        return new ConfigId(CONFIG_ID_PREFIX + index);
    }

    private ConfigServerApplication() {
        super("zone-config-servers", NodeType.config,
                ClusterSpec.Type.admin, ClusterSpec.Id.from("zone-config-servers"));
    }

    public ApplicationInfo makeApplicationInfo(ConfigserverConfig config) {
        List<HostInfo> hostInfos = new ArrayList<>();
        List<ConfigserverConfig.Zookeeperserver> zooKeeperServers = config.zookeeperserver();
        for (int index = 0; index < zooKeeperServers.size(); ++index) {
            String hostname = zooKeeperServers.get(index).hostname();
            hostInfos.add(makeHostInfo(hostname, index));
        }

        return new ApplicationInfo(
                CONFIG_SERVER_APPLICATION.getApplicationId(),
                0,
                new HostsModel(hostInfos));
    }

    private static HostInfo makeHostInfo(String hostname, int configIndex) {
        // /state/v1/health API is available with STATE and either HTTP or HTTPS.
        PortInfo portInfo = new PortInfo(4443, Arrays.asList("HTTPS", "STATE"));

        Map<String, String> properties = new HashMap<>();
        properties.put(ModelGenerator.CLUSTER_ID_PROPERTY_NAME, CLUSTER_ID.s());

        ServiceInfo serviceInfo = new ServiceInfo(
                // service name == service type for the first service of each type on each host
                SERVICE_TYPE.s(),
                SERVICE_TYPE.s(),
                Collections.singletonList(portInfo),
                properties,
                configIdFrom(configIndex).s(),
                hostname);

        return new HostInfo(hostname, Collections.singletonList(serviceInfo));
    }
}
