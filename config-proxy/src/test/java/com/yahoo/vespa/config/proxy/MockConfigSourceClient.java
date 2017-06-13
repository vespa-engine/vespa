package com.yahoo.vespa.config.proxy;

import com.yahoo.vespa.config.ConfigKey;
import com.yahoo.vespa.config.RawConfig;
import com.yahoo.vespa.config.protocol.JRTServerConfigRequest;

import java.util.Collections;
import java.util.List;

/**
 * Mock client that always returns with config immediately
 *
 * @author hmusum
 */
public class MockConfigSourceClient implements ConfigSourceClient{
    private final ClientUpdater clientUpdater;
    private final MockConfigSource configSource;

    MockConfigSourceClient(ClientUpdater clientUpdater, MockConfigSource configSource) {
        this.clientUpdater = clientUpdater;
        this.configSource = configSource;
    }

    @Override
    public RawConfig getConfig(RawConfig input, JRTServerConfigRequest request) {
        final RawConfig config = getConfig(input.getKey());
        clientUpdater.getMemoryCache().put(config);
        return config;
    }

    private RawConfig getConfig(ConfigKey<?> configKey) {
        return configSource.getConfig(configKey);
    }

    @Override
    public void cancel() {
        configSource.clear();
    }

    @Override
    public void shutdownSourceConnections() {
    }

    @Override
    public String getActiveSourceConnection() {
        return "N/A";
    }

    @Override
    public List<String> getSourceConnections() {
        return Collections.singletonList("N/A");
    }
}
