package com.yahoo.prelude.fastsearch.test.fs4mock;

import com.yahoo.fs4.mplex.Backend;
import com.yahoo.fs4.mplex.FS4Channel;

import java.util.function.Supplier;

/**
 * @author bratseth
 */
public class MockBackend extends Backend {

    private MockFSChannel channel;
    private String hostname;

    public MockBackend() {
        this("", MockFSChannel::new);
    }
    
    public MockBackend(String hostname, Supplier<MockFSChannel> channelSupplier) {
        this.hostname = hostname;
        channel = channelSupplier.get();
    }

    @Override
    public FS4Channel openChannel() { return channel; }

    @Override
    public FS4Channel openPingChannel() { return channel; }

    @Override
    public String getHost() { return hostname; }

    public MockFSChannel getChannel() { return channel; }

    public void shutdown() {}

}
