// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.component;

import com.google.common.base.Strings;
import com.yahoo.vespa.athenz.api.AthenzIdentity;
import com.yahoo.vespa.athenz.api.AthenzService;
import com.yahoo.vespa.hosted.node.admin.config.ConfigServerConfig;
import com.yahoo.vespa.hosted.node.admin.util.KeyStoreOptions;

import java.net.URI;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Information necessary to e.g. establish communication with the config servers
 *
 * @author hakon
 */
public class ConfigServerInfo {
    private final List<String> configServerHostNames;
    private final List<URI> configServerURIs;
    private final Optional<KeyStoreOptions> keyStoreOptions;
    private final Optional<KeyStoreOptions> trustStoreOptions;
    private final Optional<AthenzIdentity> athenzIdentity;

    public ConfigServerInfo(ConfigServerConfig config) {
        this.configServerHostNames = config.hosts();
        this.configServerURIs = createConfigServerUris(
                config.scheme(),
                config.hosts(),
                config.port());
        this.keyStoreOptions = createKeyStoreOptions(
                config.keyStoreConfig().path(),
                config.keyStoreConfig().password().toCharArray(),
                config.keyStoreConfig().type().name());
        this.trustStoreOptions = createKeyStoreOptions(
                config.trustStoreConfig().path(),
                config.trustStoreConfig().password().toCharArray(),
                config.trustStoreConfig().type().name());
        this.athenzIdentity = createAthenzIdentity(
                config.athenzDomain(),
                config.serviceName());
    }

    public List<String> getConfigServerHostNames() {
        return configServerHostNames;
    }

    public List<URI> getConfigServerUris() {
        return configServerURIs;
    }

    public Optional<KeyStoreOptions> getKeyStoreOptions() {
        return keyStoreOptions;
    }

    public Optional<KeyStoreOptions> getTrustStoreOptions() {
        return trustStoreOptions;
    }

    public Optional<AthenzIdentity> getAthenzIdentity() {
        return athenzIdentity;
    }

    private static List<URI> createConfigServerUris(String scheme, List<String> configServerHosts, int port) {
        return configServerHosts.stream()
                .map(hostname -> URI.create(scheme + "://" + hostname + ":" + port))
                .collect(Collectors.toList());
    }

    private static Optional<KeyStoreOptions> createKeyStoreOptions(String pathToKeyStore, char[] password, String type) {
        return Optional.ofNullable(pathToKeyStore)
                .filter(path -> !Strings.isNullOrEmpty(path))
                .map(path -> new KeyStoreOptions(Paths.get(path), password, type));
    }

    private static Optional<AthenzIdentity> createAthenzIdentity(String athenzDomain, String serviceName) {
        if (Strings.isNullOrEmpty(athenzDomain) || Strings.isNullOrEmpty(serviceName)) return Optional.empty();
        return Optional.of(new AthenzService(athenzDomain, serviceName));
    }
}
