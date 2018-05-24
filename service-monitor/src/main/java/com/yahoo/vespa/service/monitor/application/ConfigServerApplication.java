// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.monitor.application;

import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.NodeType;
import com.yahoo.vespa.applicationmodel.ApplicationInstanceId;
import com.yahoo.vespa.applicationmodel.ClusterId;
import com.yahoo.vespa.applicationmodel.ServiceType;
import com.yahoo.vespa.applicationmodel.TenantId;

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

    private ConfigServerApplication() {
        super("zone-config-servers", NodeType.config,
                ClusterSpec.Type.admin, ClusterSpec.Id.from("zone-config-servers"));
    }
}
