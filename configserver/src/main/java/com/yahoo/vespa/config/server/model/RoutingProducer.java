// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.model;

import com.yahoo.cloud.config.RoutingConfig;
import com.yahoo.config.model.api.ApplicationInfo;
import com.yahoo.config.model.api.HostInfo;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.vespa.config.server.tenant.TenantRepository;

import java.util.Map;

/**
 * Create global config based on info from the zone application
 *
 * @author can
 * @since 5.60
 */
public class RoutingProducer implements RoutingConfig.Producer {
    static final ApplicationName ROUTING_APPLICATION = ApplicationName.from("routing");

    private final Map<TenantName, Map<ApplicationId, ApplicationInfo>> models;

    public RoutingProducer(Map<TenantName, Map<ApplicationId, ApplicationInfo>> models) {
        this.models = models;
    }

    @Override
    public void getConfig(RoutingConfig.Builder builder) {
        for (Map<ApplicationId, ApplicationInfo> model : models.values()) {
            model.values().stream()
                    .filter(application -> isHostedVespaRoutingApplication(application.getApplicationId()))
                    .forEach(application -> {
                        for (HostInfo host : application.getModel().getHosts()) {
                            builder.hosts(host.getHostname());
                        }
                    });
        }
    }

    private boolean isHostedVespaRoutingApplication(ApplicationId applicationId) {
        return TenantRepository.HOSTED_VESPA_TENANT.equals(applicationId.tenant()) &&
                (ROUTING_APPLICATION.equals(applicationId.application()));
    }

}
