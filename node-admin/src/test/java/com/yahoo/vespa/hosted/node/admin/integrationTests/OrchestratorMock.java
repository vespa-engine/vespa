package com.yahoo.vespa.hosted.node.admin.integrationTests;

import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.hosted.node.admin.orchestrator.Orchestrator;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Semaphore;

/**
 * Mock with some simple logic
 * @author dybis
 */
public class OrchestratorMock implements Orchestrator {

    public static Set<HostName> running = new HashSet<>();
    public static StringBuilder requests = new StringBuilder();
    public static Optional<String> forceMultipleRequestsResponse = null;

    private static final Object monitor = new Object();

    public static final Semaphore semaphore = new Semaphore(1);

    @Override
    public boolean suspend(HostName hostName) {
        synchronized (monitor) {
            requests.append("Suspend for ").append(hostName.toString()).append("\n");
            return running.remove(hostName);
        }
    }

    @Override
    public boolean resume(HostName hostName) {
        synchronized (monitor) {
            requests.append("Resume for ").append(hostName.toString()).append("\n");
            return running.add(hostName);
        }
    }

    @Override
    public Optional<String> suspend(String parentHostName, List<String> hostNames) {
        synchronized (monitor) {
            requests.append("Suspend with parent: ").append(parentHostName).append(" and hostnames: ").append(hostNames);
            if (forceMultipleRequestsResponse != null) {
                requests.append(" - Forced response: ").append(forceMultipleRequestsResponse).append("\n");
                return forceMultipleRequestsResponse;
            }

            for (String hostName : hostNames) {
                if (! suspend(new HostName(hostName))) {
                    requests.append(" - Normal response: fail").append("\n");
                    return Optional.of("Could not suspend " + hostName);
                }
            }
        }

        requests.append(" - Normal response: success").append("\n");
        return Optional.empty();
    }

    public static void reset() {
        running = new HashSet<>();
        requests = new StringBuilder();
        forceMultipleRequestsResponse = null;
    }
}
