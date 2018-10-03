// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.fastsearch.test.fs4mock;

import com.yahoo.fs4.mplex.Backend;
import com.yahoo.fs4.mplex.FS4Channel;

/**
 * @author bratseth
 */
public class MockBackend extends Backend {

    private String hostname;
    private final long activeDocumentsInBackend;
    private final boolean working;

    /** Created lazily as we want to have just one but it depends on the channel */
    private MockFSChannel channel = null;

    public MockBackend() {
        this("", 0L, true);
    }
    
    public MockBackend(String hostname, long activeDocumentsInBackend, boolean working) {
        super();
        this.hostname = hostname;
        this.activeDocumentsInBackend = activeDocumentsInBackend;
        this.working = working;
    }

    @Override
    public FS4Channel openChannel() {
        if (channel == null)
            channel = working ? new MockFSChannel(activeDocumentsInBackend, this)
                              : new NonWorkingMockFSChannel(this);
        return channel;
    }

    @Override
    public FS4Channel openPingChannel() { return openChannel(); }

    @Override
    public String getHost() { return hostname; }

    /** Returns the channel in use or null if no channel has been used yet */
    public MockFSChannel getChannel() { return channel; }

    public void shutdown() {}

}
