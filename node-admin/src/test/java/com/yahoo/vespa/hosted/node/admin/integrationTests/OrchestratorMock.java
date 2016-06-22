package com.yahoo.vespa.hosted.node.admin.integrationTests;

import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.hosted.node.admin.orchestrator.Orchestrator;

import java.util.List;
import java.util.Optional;

/**
 * Mock with some simple logic
 * @author dybis
 */
public class OrchestratorMock implements Orchestrator {

    public Optional<String> suspendReturnValue = Optional.empty();

    @Override
    public boolean suspend(HostName hostName) {
        return false;
    }

    @Override
    public boolean resume(HostName hostName) {
        return false;
    }

    @Override
    public Optional<String> suspend(String parentHostName, List<String> hostNames) {
        return suspendReturnValue;
    }

    @Override
    public Optional<String> resume(List<String> hostName) {
        return Optional.empty();
    }
}
