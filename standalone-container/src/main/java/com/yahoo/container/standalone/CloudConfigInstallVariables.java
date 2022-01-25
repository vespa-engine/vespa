// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
        return Optional.ofNullable(System.getenv("VESPA_CONFIGSERVER_RPC_PORT"))
                .or(() -> getRawInstallVariable("services.port_configserver_rpc"))
                .map(Integer::parseInt);
    }

    @Override
    public Optional<Boolean> multiTenant() {
        return Optional.ofNullable(System.getenv("VESPA_CONFIGSERVER_MULTITENANT"))
                .or(() -> getInstallVariable("multitenant"))
                .map(Boolean::parseBoolean);
    }

    @Override
    public ConfigServer[] allConfigServers() {
        return Optional.ofNullable(System.getenv("VESPA_CONFIGSERVERS"))
                .or(() -> getRawInstallVariable("services.addr_configserver"))
                .map(CloudConfigInstallVariables::toConfigServers)
                .orElseGet(() -> new ConfigServer[0]);
    }

    @Override
    public int[] configServerZookeeperIds() {
        return Optional.ofNullable(System.getenv("VESPA_CONFIGSERVER_ZOOKEEPER_IDS"))
                .map(CloudConfigInstallVariables::multiValueParameterStream)
                .orElseGet(Stream::empty)
                .mapToInt(Integer::valueOf)
                .toArray();
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
        return getInstallVariable("zookeeper_quorumPort", Integer::parseInt);
    }

    @Override
    public Optional<Integer> zookeeperElectionPort() {
        return getInstallVariable("zookeeper_electionPort", Integer::parseInt);
    }

    @Override
    public Optional<String> environment() {
        return Optional.ofNullable(System.getenv("VESPA_ENVIRONMENT"))
                .or(() -> getInstallVariable("environment"));
    }

    @Override
    public Optional<String> region() {
        return Optional.ofNullable(System.getenv("VESPA_REGION"))
                .or(() -> getInstallVariable("region"));
    }

    @Override
    public Optional<String> system() {
        return Optional.ofNullable(System.getenv("VESPA_SYSTEM"))
                .or(() -> getInstallVariable("system"));
    }

    @Override
    public Optional<String> cloud() {
        return Optional.ofNullable(System.getenv("VESPA_CLOUD"));
    }

    @Override
    public Optional<Boolean> useVespaVersionInRequest() {
        return getInstallVariable("use_vespa_version_in_request", Boolean::parseBoolean);
    }

    @Override
    public Optional<Boolean> hostedVespa() {
        return Optional.ofNullable(System.getenv("VESPA_CONFIGSERVER_HOSTED"))
                .or(() -> getInstallVariable("hosted_vespa"))
                .map(Boolean::parseBoolean);
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
        return getRawInstallVariable("cloudconfig_server." + name).map(converter);
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
