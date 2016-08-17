package com.yahoo.prelude.fastsearch.test.fs4mock;

import com.yahoo.fs4.mplex.Backend;
import com.yahoo.prelude.fastsearch.FS4ResourcePool;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * @author bratseth
 */
public class MockFS4ResourcePool extends FS4ResourcePool {

    private final Map<String, Integer> requestsPerBackend = new HashMap<>();
    private final Set<String> nonRespondingBackends = new HashSet<>();

    public MockFS4ResourcePool() {
        super(1);
    }

    @Override
    public Backend getBackend(String hostname, int port) {
        countRequest(hostname + ":" + port);
        if (nonRespondingBackends.contains(hostname))
            return new MockBackend(hostname, NonWorkingMockFSChannel::new);
        else
            return new MockBackend(hostname, MockFSChannel::new);
    }

    /** Returns the number of times a backend for this hostname and port has been requested */
    public int requestCount(String hostname, int port) {
        return requestsPerBackend.getOrDefault(hostname + ":" + port, 0);
    }

    private void countRequest(String hostAndPort) {
        requestsPerBackend.put(hostAndPort, requestsPerBackend.getOrDefault(hostAndPort, 0) + 1);
    }

    public void setResponding(String hostname, boolean responding) {
        if (responding)
            nonRespondingBackends.remove(hostname);
        else
            nonRespondingBackends.add(hostname);
    }

}
