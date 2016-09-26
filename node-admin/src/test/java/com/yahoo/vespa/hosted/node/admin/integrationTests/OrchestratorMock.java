// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.integrationTests;

import com.yahoo.vespa.hosted.node.admin.orchestrator.Orchestrator;

import java.util.List;
import java.util.Optional;

/**
 * Mock with some simple logic
 *
 * @author dybis
 */
public class OrchestratorMock implements Orchestrator {
    private final CallOrderVerifier callOrder;

    private boolean forceSingleSuspendResponse = true;
    private boolean forceSingleResumeResponse = true;
    private Optional<String> forceGroupSuspendResponse = Optional.empty();

    private static final Object monitor = new Object();

    public OrchestratorMock(CallOrderVerifier callOrder) {
        this.callOrder = callOrder;
    }

    @Override
    public boolean suspend(String hostName) {
        synchronized (monitor) {
            return forceSingleSuspendResponse;
        }
    }

    @Override
    public boolean resume(String hostName) {
        synchronized (monitor) {
            callOrder.add("Resume for " + hostName);
            return forceSingleResumeResponse;
        }
    }

    @Override
    public Optional<String> suspend(String parentHostName, List<String> hostNames) {
        synchronized (monitor) {
            callOrder.add("Suspend with parent: " + parentHostName + " and hostnames: " + hostNames +
                    " - Forced response: " + forceGroupSuspendResponse);
            return forceGroupSuspendResponse;
        }
    }

    public void setForceSingleSuspendResponse(boolean forceSingleSuspendResponse) {
        synchronized (monitor) {
            this.forceSingleSuspendResponse = forceSingleSuspendResponse;
        }
    }

    public void setForceSingleResumeResponse(boolean forceSingleResumeResponse) {
        synchronized (monitor) {
            this.forceSingleResumeResponse = forceSingleResumeResponse;
        }
    }

    public void setForceGroupSuspendResponse(Optional<String> forceGroupSuspendResponse) {
        synchronized (monitor) {
            this.forceGroupSuspendResponse = forceGroupSuspendResponse;
        }
    }
}
