// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.controller;

import com.yahoo.vespa.applicationmodel.ServiceInstance;

import java.util.Collection;

/**
 * @author bakksjo
 */
public interface ClusterControllerClientFactory {
    ClusterControllerClient createClient(Collection<? extends ServiceInstance<?>> clusterControllers, String clusterName);
}
