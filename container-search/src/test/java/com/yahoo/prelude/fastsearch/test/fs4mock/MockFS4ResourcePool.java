// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
    private final Map<String, Long> activeDocumentsInBackend = new HashMap<>();    
    private final long testingThreadId;
    
    public MockFS4ResourcePool() {
        super(1);
        this.testingThreadId = Thread.currentThread().getId();
    }

    @Override
    public Backend getBackend(String hostname, int port) {
        System.out.println("Got request to " + hostname + ":" + port);
        countRequest(hostname + ":" + port);
        if (nonRespondingBackends.contains(hostname))
            return new MockBackend(hostname, NonWorkingMockFSChannel::new);
        else
            return new MockBackend(hostname, 
                                   () -> new MockFSChannel(activeDocumentsInBackend.getOrDefault(hostname, 0L)));
    }

    /** 
     * Returns the number of times a backend for this hostname and port has been requested 
     * from the thread creating this 
     */
    public int requestCount(String hostname, int port) {
        return requestsPerBackend.getOrDefault(hostname + ":" + port, 0);
    }
    
    /** Sets the number of active documents the given host will report to have in ping responses */
    public void setActiveDocuments(String hostname, long activeDocuments) {
        activeDocumentsInBackend.put(hostname, activeDocuments);
    }

    private void countRequest(String hostAndPort) {
        // ignore requests from the ping thread to avoid timing issues
        if (Thread.currentThread().getId() != testingThreadId) return;

        requestsPerBackend.put(hostAndPort, requestsPerBackend.getOrDefault(hostAndPort, 0) + 1);
    }

    public void setResponding(String hostname, boolean responding) {
        if (responding)
            nonRespondingBackends.remove(hostname);
        else
            nonRespondingBackends.add(hostname);
    }

}
