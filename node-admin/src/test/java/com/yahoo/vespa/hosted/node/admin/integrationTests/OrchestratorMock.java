// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.integrationTests;

import com.yahoo.vespa.hosted.node.admin.configserver.orchestrator.Orchestrator;

import java.util.List;

/**
 * Mock with some simple logic
 *
 * @author dybis
 */
public class OrchestratorMock implements Orchestrator {
    private final CallOrderVerifier callOrderVerifier;

    OrchestratorMock(CallOrderVerifier callOrderVerifier) {
        this.callOrderVerifier = callOrderVerifier;
    }

    @Override
    public void suspend(String hostName) {
        callOrderVerifier.add("Suspend for " + hostName);
    }

    @Override
    public void resume(String hostName) {
        callOrderVerifier.add("Resume for " + hostName);
    }

    @Override
    public void suspend(String parentHostName, List<String> hostNames) {
        callOrderVerifier.add("Suspend with parent: " + parentHostName + " and hostnames: " + hostNames);
    }
}
