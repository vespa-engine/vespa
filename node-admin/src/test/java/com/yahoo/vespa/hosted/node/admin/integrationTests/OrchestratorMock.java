// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.integrationTests;

import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.hosted.node.admin.orchestrator.Orchestrator;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.Semaphore;

/**
 * Mock with some simple logic
 *
 * @author dybis
 */
public class OrchestratorMock implements Orchestrator {

    private static StringBuilder requests = new StringBuilder();

    private static boolean forceSingleSuspendResponse;
    private static boolean forceSingleResumeResponse;
    private static Optional<String> forceGroupSuspendResponse;

    private static final Object monitor = new Object();

    public static final Semaphore semaphore = new Semaphore(1);

    static {
        reset();
    }

    public OrchestratorMock() {
        if (semaphore.tryAcquire()) {
            throw new RuntimeException("OrchestratorMock.semaphore must be acquired before using OrchestratorMock");
        }
    }

    @Override
    public boolean suspend(HostName hostName) {
        synchronized (monitor) {
            return forceSingleSuspendResponse;
        }
    }

    @Override
    public boolean resume(HostName hostName) {
        synchronized (monitor) {
            requests.append("Resume for ").append(hostName).append("\n");
            return forceSingleResumeResponse;
        }
    }

    @Override
    public Optional<String> suspend(String parentHostName, List<String> hostNames) {
        synchronized (monitor) {
            requests.append("Suspend with parent: ").append(parentHostName)
                    .append(" and hostnames: ").append(hostNames)
                    .append(" - Forced response: ").append(forceGroupSuspendResponse).append("\n");
            return forceGroupSuspendResponse;
        }
    }

    public static String getRequests() {
        synchronized (monitor) {
            return requests.toString();
        }
    }

    public static void setForceSingleSuspendResponse(boolean forceSingleSuspendResponse) {
        synchronized (monitor) {
            OrchestratorMock.forceSingleSuspendResponse = forceSingleSuspendResponse;
        }
    }

    public static void setForceSingleResumeResponse(boolean forceSingleResumeResponse) {
        synchronized (monitor) {
            OrchestratorMock.forceSingleResumeResponse = forceSingleResumeResponse;
        }
    }

    public static void setForceGroupSuspendResponse(Optional<String> forceGroupSuspendResponse) {
        synchronized (monitor) {
            OrchestratorMock.forceGroupSuspendResponse = forceGroupSuspendResponse;
        }
    }

    public static void reset() {
        synchronized (monitor) {
            requests = new StringBuilder();
            forceSingleResumeResponse = true;
            forceSingleSuspendResponse = true;
            forceGroupSuspendResponse = Optional.empty();
        }
    }
}
