// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.standalone;

import com.yahoo.vespa.model.container.configserver.option.CloudConfigOptions;

import java.util.Arrays;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * @author bjorncs
 */
public class CloudConfigInstallVariables implements CloudConfigOptions {

    @Override
    public Optional<Integer> rpcPort() {
        return getInstallVariable("port_configserver_rpc", "services", Integer::parseInt);
    }

    @Override
    public Optional<Boolean> multiTenant() {
        return getInstallVariable("multitenant", Boolean::parseBoolean);
    }

    @Override
    public ConfigServer[] allConfigServers() {
        return Optional.ofNullable(System.getenv("VESPA_CONFIGSERVERS"))
                .map(Optional::of) // TODO Rewrite Optional.or() with Java 9
                .orElseGet(() -> getRawInstallVariable("services.addr_configserver"))
                .map(CloudConfigInstallVariables::toConfigServers)
                .orElseGet(() -> new ConfigServer[0]);
    }

    @Override
    public Optional<Long> zookeeperBarrierTimeout() {
        return getInstallVariable("zookeeper_barrier_timeout", Long::parseLong);
    }

    @Override
    public Optional<Long> sessionLifeTimeSecs() {
        return getInstallVariable("session_lifetime", Long::parseLong);
    }

    @Override
    public String[] configModelPluginDirs() {
        return getRawInstallVariable("cloudconfig_server.config_model_plugin_dirs")
                .map(CloudConfigInstallVariables::toConfigModelsPluginDir)
                .orElseGet(() -> new String[0]);
    }

    @Override
    public Optional<Integer> zookeeperClientPort() {
        return getInstallVariable("zookeeper_clientPort", Integer::parseInt);
    }

    @Override
    public Optional<Integer> zookeeperQuorumPort() {
        return getInstallVariable("zookeeper_quoromPort", Integer::parseInt);
    }

    @Override
    public Optional<Integer> zookeeperElectionPort() {
        return getInstallVariable("zookeeper_electionPort", Integer::parseInt);
    }

    @Override
    public Optional<String> payloadCompressionType() {
        return getInstallVariable("payload_compression_type", Function.identity());
    }

    @Override
    public Optional<String> environment() {
        return getInstallVariable("environment");
    }

    @Override
    public Optional<String> region() {
        return getInstallVariable("region");
    }

    @Override
    public Optional<String> system() {
        return getInstallVariable("system");
    }

    @Override
    public Optional<String> defaultFlavor() {
        return getInstallVariable("default_flavor");
    }

    @Override
    public Optional<String> defaultAdminFlavor() {
        return getInstallVariable("default_admin_flavor");
    }

    @Override
    public Optional<String> defaultContainerFlavor() {
        return getInstallVariable("default_container_flavor");
    }

    @Override
    public Optional<String> defaultContentFlavor() {
        return getInstallVariable("default_content_flavor");
    }

    @Override
    public Optional<Boolean> useVespaVersionInRequest() {
        return getInstallVariable("use_vespa_version_in_request", Boolean::parseBoolean);
    }

    @Override
    public Optional<Boolean> hostedVespa() {
        return getInstallVariable("hosted_vespa", Boolean::parseBoolean);
    }

    @Override
    public Optional<Integer> numParallelTenantLoaders() {
        return getInstallVariable("num_parallel_tenant_loaders", Integer::parseInt);
    }

    @Override
    public Optional<String> loadBalancerAddress() {
        return getInstallVariable("load_balancer_address");
    }

    @Override
    public Optional<String> athenzDnsSuffix() {
        return getInstallVariable("athenz_dns_suffix");
    }

    @Override
    public Optional<String> ztsUrl() {
        return getInstallVariable("zts_url");
    }

    static ConfigServer[] toConfigServers(String configserversString) {
        return multiValueParameterStream(configserversString)
                .map(CloudConfigInstallVariables::toConfigServer)
                .toArray(ConfigServer[]::new);
    }

    static ConfigServer toConfigServer(String configserverString) {
        try {
            String[] hostPortTuple = configserverString.split(":");
            if (configserverString.contains(":")) {
                return new ConfigServer(hostPortTuple[0], Optional.of(Integer.parseInt(hostPortTuple[1])));
            } else {
                return new ConfigServer(configserverString, Optional.empty());
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid config server " + configserverString, e);
        }
    }

    static String[] toConfigModelsPluginDir(String configModelsPluginDirString) {
        return multiValueParameterStream(configModelsPluginDirString).toArray(String[]::new);
    }

    private static Optional<String> getInstallVariable(String name) {
        return getInstallVariable(name, Function.identity());
    }

    private static <T> Optional<T> getInstallVariable(String name, Function<String, T> converter) {
        return getInstallVariable(name, "cloudconfig_server", converter);
    }

    private static <T> Optional<T> getInstallVariable(String name, String installPackage, Function<String, T> converter) {
        return getRawInstallVariable(installPackage + "." + name).map(converter);
    }

    private static Optional<String> getRawInstallVariable(String name) {
        return Optional.ofNullable(
                Optional.ofNullable(System.getenv(name.replace(".", "__")))
                        .orElseGet(() -> System.getProperty(name)));
    }

    private static Stream<String> multiValueParameterStream(String param) {
        return Arrays.stream(param.split("[, ]")).filter(value -> !value.isEmpty());
    }
}
