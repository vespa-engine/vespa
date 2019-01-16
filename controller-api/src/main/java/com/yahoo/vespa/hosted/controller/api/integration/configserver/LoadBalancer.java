// Copyright 2019 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.configserver;

import com.yahoo.vespa.hosted.controller.api.identifiers.ApplicationId;
import com.yahoo.vespa.hosted.controller.api.identifiers.InstanceId;
import com.yahoo.vespa.hosted.controller.api.identifiers.TenantId;

public class LoadBalancer {
    private final String id;
    private final TenantId tenant;
    private final ApplicationId application;
    private final InstanceId instance;
    private final String cluster;
    private final String hostname;

    public LoadBalancer(String id, TenantId tenant, ApplicationId application, InstanceId instance, String cluster, String hostname) {
        this.id = id;
        this.tenant = tenant;
        this.application = application;
        this.instance = instance;
        this.cluster = cluster;
        this.hostname = hostname;
    }

    public String id() {
        return id;
    }

    public TenantId tenant() {
        return tenant;
    }

    public ApplicationId application() {
        return application;
    }

    public InstanceId instance() {
        return instance;
    }

    public String cluster() {
        return cluster;
    }

    public String hostname() {
        return hostname;
    }
}
