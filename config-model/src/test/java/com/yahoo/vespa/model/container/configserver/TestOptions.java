// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.configserver;

import com.yahoo.vespa.model.container.configserver.option.CloudConfigOptions;

import java.util.Optional;

/**
 * @author Ulf Lilleengen
 */
public class TestOptions implements CloudConfigOptions {
    private Optional<Integer> rpcPort = Optional.empty();
    private Optional<String> environment = Optional.empty();
    private Optional<String> region = Optional.empty();
    private Optional<String> defaultFlavor = Optional.empty();
    private Optional<String> defaultAdminFlavor = Optional.empty();
    private Optional<String> defaultContainerFlavor = Optional.empty();
    private Optional<String> defaultContentFlavor = Optional.empty();
    private Optional<Boolean> useVespaVersionInRequest = Optional.empty();
    private Optional<Boolean> hostedVespa = Optional.empty();
    private Optional<Integer> numParallelTenantLoaders = Optional.empty();

    @Override
    public Optional<Integer> rpcPort() {
        return rpcPort;
    }

    public TestOptions rpcPort(int port) {
        this.rpcPort = Optional.of(port);
        return this;
    }

    public TestOptions useVespaVersionInRequest(boolean useVespaVersionInRequest) {
        this.useVespaVersionInRequest = Optional.of(useVespaVersionInRequest);
        return this;
    }

    @Override
    public Optional<Boolean> multiTenant() { return Optional.empty();  }

    @Override
    public Optional<Boolean> hostedVespa() {
        return hostedVespa;
    }

    @Override
    public ConfigServer[] allConfigServers() {
        return new ConfigServer[0];
    }

    @Override
    public Optional<Integer> zookeeperClientPort() {
        return Optional.empty();
    }

    @Override
    public String[] configModelPluginDirs() {
        return new String[0];
    }

    @Override
    public Optional<Long> sessionLifeTimeSecs() {
        return Optional.empty();
    }

    @Override
    public Optional<Long> zookeeperBarrierTimeout() {
        return Optional.empty();
    }

    @Override
    public Optional<Integer> zookeeperElectionPort() {
        return Optional.empty();
    }

    @Override
    public Optional<Integer> zookeeperQuorumPort() {
        return Optional.empty();
    }

    @Override
    public Optional<String> payloadCompressionType() { return Optional.empty(); }

    @Override
    public Optional<String> environment() { return environment; }

    @Override
    public Optional<String> region() { return region; }

    @Override
    public Optional<String> system() { return Optional.empty(); }

    @Override
    public Optional<String> defaultFlavor() { return defaultFlavor; }

    @Override
    public Optional<String> defaultAdminFlavor() { return defaultAdminFlavor; }

    @Override
    public Optional<String> defaultContainerFlavor() { return defaultContainerFlavor; }

    @Override
    public Optional<String> defaultContentFlavor() { return defaultContentFlavor; }

    @Override
    public Optional<Boolean> useVespaVersionInRequest() { return useVespaVersionInRequest; }

    @Override
    public Optional<Integer> numParallelTenantLoaders() { return numParallelTenantLoaders; }

    @Override
    public Optional<String> dockerRegistry() { return Optional.empty(); }

    @Override
    public Optional<String> dockerVespaBaseImage() { return Optional.empty(); }

    @Override
    public Optional<String> loadBalancerAddress() { return Optional.empty(); }

    public TestOptions numParallelTenantLoaders(int numLoaders) {
        this.numParallelTenantLoaders = Optional.of(numLoaders);
        return this;
    }

    public TestOptions environment(String environment) {
        this.environment = Optional.of(environment);
        return this;
    }

    public TestOptions region(String region) {
        this.region = Optional.of(region);
        return this;
    }

    public TestOptions hostedVespa(boolean hostedVespa) {
        this.hostedVespa = Optional.of(hostedVespa);
        return this;
    }
}
