// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.standalone;

import com.yahoo.vespa.model.container.configserver.option.ConfigOptions;

import java.util.Arrays;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * @author bjorncs
 */
public class ConfigEnvironmentVariables implements ConfigOptions {

    @Override
    public Optional<Integer> rpcPort() {
        return Optional.ofNullable(System.getenv("VESPA_CONFIGSERVER_RPC_PORT"))
                .map(Integer::parseInt);
    }

    @Override
    public Optional<Boolean> multiTenant() {
        return Optional.ofNullable(System.getenv("VESPA_CONFIGSERVER_MULTITENANT"))
                .map(Boolean::parseBoolean);
    }

    @Override
    public ConfigServer[] allConfigServers() {
        return Optional.ofNullable(System.getenv("VESPA_CONFIGSERVERS"))
                .map(ConfigEnvironmentVariables::toConfigServers)
                .orElseGet(() -> new ConfigServer[0]);
    }

    @Override
    public int[] configServerZookeeperIds() {
        return Optional.ofNullable(System.getenv("VESPA_CONFIGSERVER_ZOOKEEPER_IDS"))
                .map(ConfigEnvironmentVariables::multiValueParameterStream)
                .orElseGet(Stream::empty)
                .mapToInt(Integer::valueOf)
                .toArray();
    }

    @Override
    public Optional<Long> zookeeperBarrierTimeout() {
        return  Optional.ofNullable(System.getenv("VESPA_CONFIGSERVER_ZOOKEEPER_BARRIER_TIMEOUT"))
                .map(Long::parseLong);
    }

    @Override
    public Integer zookeeperJuteMaxBuffer() {
        return  Optional.ofNullable(System.getenv("VESPA_CONFIGSERVER_ZOOKEEPER_JUTE_MAX_BUFFER"))
                .map(Integer::parseInt)
                .orElse(104857600);
    }

    @Override
    public Optional<String> environment() {
        return Optional.ofNullable(System.getenv("VESPA_ENVIRONMENT"));
    }

    @Override
    public Optional<String> region() {
        return Optional.ofNullable(System.getenv("VESPA_REGION"));
    }

    @Override
    public Optional<String> system() {
        return Optional.ofNullable(System.getenv("VESPA_SYSTEM"));
    }

    @Override
    public Optional<String> cloud() {
        return Optional.ofNullable(System.getenv("VESPA_CLOUD"));
    }

    @Override
    public Optional<Boolean> useVespaVersionInRequest() {
        return Optional.ofNullable(System.getenv("VESPA_CONFIGSERVER_USE_VERSION_IN_CONFIG_REQUEST"))
                .or(() -> getInstallVariable("use_vespa_version_in_request"))
                .map(Boolean::parseBoolean);
    }

    @Override
    public Optional<Boolean> hostedVespa() {
        return Optional.ofNullable(System.getenv("VESPA_CONFIGSERVER_HOSTED"))
                .map(Boolean::parseBoolean);
    }

    @Override
    public String zooKeeperSnapshotMethod() {
        String vespaZookeeperSnapshotMethod = System.getenv("VESPA_ZOOKEEPER_SNAPSHOT_METHOD");
        return vespaZookeeperSnapshotMethod == null ? "" : vespaZookeeperSnapshotMethod;
    }

    static ConfigServer[] toConfigServers(String configserversString) {
        return multiValueParameterStream(configserversString)
                .map(ConfigEnvironmentVariables::toConfigServer)
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
