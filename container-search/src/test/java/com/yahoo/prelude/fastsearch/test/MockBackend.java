package com.yahoo.prelude.fastsearch.test;

import com.yahoo.fs4.mplex.Backend;
import com.yahoo.fs4.mplex.FS4Channel;

/**
 * @author bratseth
 */
class MockBackend extends Backend {

    private MockFSChannel channel;

    public MockBackend() {
        channel = new MockFSChannel(this, 1);
    }

    public FS4Channel openChannel() {
        return channel;
    }

    public MockFSChannel getChannel() { return channel; }

    public void shutdown() {}

}
