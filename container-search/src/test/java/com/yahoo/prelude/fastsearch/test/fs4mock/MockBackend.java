package com.yahoo.prelude.fastsearch.test.fs4mock;

import com.yahoo.fs4.mplex.Backend;
import com.yahoo.fs4.mplex.FS4Channel;

/**
 * @author bratseth
 */
public class MockBackend extends Backend {

    private MockFSChannel channel;

    public MockBackend() {
        this(true);
    }
    
    public MockBackend(boolean working) {
        if (working)
            channel = new MockFSChannel(this, 1);
        else
            channel = new NonWorkingMockFSChannel();
    }

    @Override
    public FS4Channel openChannel() {
        return channel;
    }

    public MockFSChannel getChannel() { return channel; }

    public void shutdown() {}

}
