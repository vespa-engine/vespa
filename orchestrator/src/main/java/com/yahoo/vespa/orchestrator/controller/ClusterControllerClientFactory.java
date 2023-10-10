// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.controller;

import com.yahoo.vespa.applicationmodel.HostName;

import java.util.List;

/**
 * @author bakksjo
 */
public interface ClusterControllerClientFactory {

    ClusterControllerClient createClient(List<HostName> clusterControllers, String clusterName);

}
