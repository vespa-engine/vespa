// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.service;

/**
 * Mock config sentinel
 *
 * @author hmusum
 */
public class MockConfigSentinelClient extends ConfigSentinelClient implements AutoCloseable {

    private final ConfigSentinelDummy configSentinel;

    public MockConfigSentinelClient(ConfigSentinelDummy configSentinel) {
        super();
        this.configSentinel = configSentinel;
    }

    @Override
    String sentinelLs() {
        return configSentinel.getServiceList();
    }

    @Override
    public void close() {
        super.deconstruct();
    }
}
