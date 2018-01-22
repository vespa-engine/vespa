// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.util;

import com.google.common.base.Strings;
import com.yahoo.vespa.defaults.Defaults;
import com.yahoo.vespa.hosted.dockerapi.ContainerName;
import com.yahoo.vespa.hosted.node.admin.ConfigServerConfig;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.TimeZone;
import java.util.stream.Collectors;

/**
 * Various utilities for getting values from node-admin's environment. Immutable.
 *
 * @author bakksjo
 * @author hmusum
 */
public class Environment {
    private static final DateFormat filenameFormatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");
    public static final String APPLICATION_STORAGE_CLEANUP_PATH_PREFIX = "cleanup_";

    private static final String ENVIRONMENT = "ENVIRONMENT";
    private static final String REGION = "REGION";
    private static final String LOGSTASH_NODES = "LOGSTASH_NODES";
    private static final String COREDUMP_FEED_ENDPOINT = "COREDUMP_FEED_ENDPOINT";

    private final List<URI> configServerHosts;
    private final String environment;
    private final String region;
    private final String parentHostHostname;
    private final InetAddressResolver inetAddressResolver;
    private final PathResolver pathResolver;
    private final List<String> logstashNodes;
    private final String feedEndpoint;
    private final Optional<KeyStoreOptions> keyStoreOptions;
    private final Optional<KeyStoreOptions> trustStoreOptions;

    static {
        filenameFormatter.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    public Environment(ConfigServerConfig configServerConfig) {
        this(createConfigServerUris(
                configServerConfig.scheme(),
                configServerConfig.hosts(),
                configServerConfig.port()),

             getEnvironmentVariable(ENVIRONMENT),
             getEnvironmentVariable(REGION),
             Defaults.getDefaults().vespaHostname(),
             new InetAddressResolver(),
             new PathResolver(),
             getLogstashNodesFromEnvironment(),
             getEnvironmentVariable(COREDUMP_FEED_ENDPOINT),

             createKeyStoreOptions(
                     configServerConfig.keyStoreConfig().path(),
                     configServerConfig.keyStoreConfig().password().toCharArray(),
                     configServerConfig.keyStoreConfig().type().name()),
             createKeyStoreOptions(
                     configServerConfig.trustStoreConfig().path(),
                     configServerConfig.trustStoreConfig().password().toCharArray(),
                     configServerConfig.trustStoreConfig().type().name())
        );
    }

    public Environment(List<URI> configServerHosts,
                       String environment,
                       String region,
                       String parentHostHostname,
                       InetAddressResolver inetAddressResolver,
                       PathResolver pathResolver,
                       List<String> logstashNodes,
                       String feedEndpoint,
                       Optional<KeyStoreOptions> keyStoreOptions,
                       Optional<KeyStoreOptions> trustStoreOptions) {
        this.configServerHosts = configServerHosts;
        this.environment = environment;
        this.region = region;
        this.parentHostHostname = parentHostHostname;
        this.inetAddressResolver = inetAddressResolver;
        this.pathResolver = pathResolver;
        this.logstashNodes = logstashNodes;
        this.feedEndpoint = feedEndpoint;
        this.keyStoreOptions = keyStoreOptions;
        this.trustStoreOptions = trustStoreOptions;
    }

    public List<URI> getConfigServerUris() { return configServerHosts; }

    public String getEnvironment() {
        return environment;
    }

    public String getRegion() {
        return region;
    }

    public String getParentHostHostname() {
        return parentHostHostname;
    }

    private static String getEnvironmentVariable(String name) {
        final String value = System.getenv(name);
        if (Strings.isNullOrEmpty(value)) {
            throw new IllegalStateException(String.format("Environment variable %s not set", name));
        }
        return value;
    }

    public String getZone() {
        return getEnvironment() + "." + getRegion();
    }

    private static List<URI> createConfigServerUris(String scheme, List<String> configServerHosts, int port) {
        return configServerHosts.stream()
                .map(hostname -> URI.create(scheme + "://" + hostname + ":" + port))
                .collect(Collectors.toList());
    }

    private static List<String> getLogstashNodesFromEnvironment() {
        String logstashNodes = System.getenv(LOGSTASH_NODES);
        if(Strings.isNullOrEmpty(logstashNodes)) {
            return Collections.emptyList();
        }
        return Arrays.asList(logstashNodes.split("[,\\s]+"));
    }

    private static Optional<KeyStoreOptions> createKeyStoreOptions(String pathToKeyStore, char[] password, String type) {
        return Optional.ofNullable(pathToKeyStore)
                .filter(path -> !Strings.isNullOrEmpty(path))
                .map(path -> new KeyStoreOptions(Paths.get(path), password, type));
    }

    public InetAddress getInetAddressForHost(String hostname) throws UnknownHostException {
        return inetAddressResolver.getInetAddressForHost(hostname);
    }

    public PathResolver getPathResolver() {
        return pathResolver;
    }

    public String getCoredumpFeedEndpoint() {
        return feedEndpoint;
    }

    /**
     * Absolute path in node admin to directory with processed and reported core dumps
     */
    public Path pathInNodeAdminToDoneCoredumps() {
        return pathResolver.getApplicationStoragePathForNodeAdmin().resolve("processed-coredumps");
    }

    /**
     * Absolute path in node admin container to the node cleanup directory.
     */
    public Path pathInNodeAdminToNodeCleanup(ContainerName containerName) {
        return pathResolver.getApplicationStoragePathForNodeAdmin()
                .resolve(APPLICATION_STORAGE_CLEANUP_PATH_PREFIX + containerName.asString() +
                        "_" + filenameFormatter.format(Date.from(Instant.now())));
    }

    /**
     * Translates an absolute path in node agent container to an absolute path in node admin container.
     * @param containerName name of the node agent container
     * @param absolutePathInNode absolute path in that container
     * @return the absolute path in node admin container pointing at the same inode
     */
    public Path pathInNodeAdminFromPathInNode(ContainerName containerName, String absolutePathInNode) {
        Path pathInNode = Paths.get(absolutePathInNode);
        if (! pathInNode.isAbsolute()) {
            throw new IllegalArgumentException("The specified path in node was not absolute: " + absolutePathInNode);
        }

        return pathResolver.getApplicationStoragePathForNodeAdmin()
                .resolve(containerName.asString())
                .resolve(PathResolver.ROOT.relativize(pathInNode));
    }

    /**
     * Translates an absolute path in node agent container to an absolute path in host.
     * @param containerName name of the node agent container
     * @param absolutePathInNode absolute path in that container
     * @return the absolute path in host pointing at the same inode
     */
    public Path pathInHostFromPathInNode(ContainerName containerName, String absolutePathInNode) {
        Path pathInNode = Paths.get(absolutePathInNode);
        if (! pathInNode.isAbsolute()) {
            throw new IllegalArgumentException("The specified path in node was not absolute: " + absolutePathInNode);
        }

        return pathResolver.getApplicationStoragePathForHost()
                .resolve(containerName.asString())
                .resolve(PathResolver.ROOT.relativize(pathInNode));
    }

    public List<String> getLogstashNodes() {
        return logstashNodes;
    }

    public Optional<KeyStoreOptions> getKeyStoreOptions() {
        return keyStoreOptions;
    }

    public Optional<KeyStoreOptions> getTrustStoreOptions() {
        return trustStoreOptions;
    }


    public static class Builder {
        private List<URI> configServerHosts = Collections.emptyList();
        private String environment;
        private String region;
        private String parentHostHostname;
        private InetAddressResolver inetAddressResolver;
        private PathResolver pathResolver;
        private List<String> logstashNodes = Collections.emptyList();
        private String feedEndpoint;
        private KeyStoreOptions keyStoreOptions;
        private KeyStoreOptions trustStoreOptions;

        public Builder configServerUris(String... hosts) {
            configServerHosts = Arrays.stream(hosts)
                    .map(URI::create)
                    .collect(Collectors.toList());
            return this;
        }

        public Builder environment(String environment) {
            this.environment = environment;
            return this;
        }

        public Builder region(String region) {
            this.region = region;
            return this;
        }

        public Builder parentHostHostname(String parentHostHostname) {
            this.parentHostHostname = parentHostHostname;
            return this;
        }

        public Builder inetAddressResolver(InetAddressResolver inetAddressResolver) {
            this.inetAddressResolver = inetAddressResolver;
            return this;
        }

        public Builder pathResolver(PathResolver pathResolver) {
            this.pathResolver = pathResolver;
            return this;
        }

        public Builder logstashNodes(List<String> hosts) {
            this.logstashNodes = hosts;
            return this;
        }

        public Builder feedEndpoint(String feedEndpoint) {
            this.feedEndpoint = feedEndpoint;
            return this;
        }

        public Builder keyStoreOptions(KeyStoreOptions keyStoreOptions) {
            this.keyStoreOptions = keyStoreOptions;
            return this;
        }

        public Builder trustStoreOptions(KeyStoreOptions trustStoreOptions) {
            this.trustStoreOptions = trustStoreOptions;
            return this;
        }


        public Environment build() {
            return new Environment(configServerHosts, environment, region, parentHostHostname, inetAddressResolver,
                                   pathResolver, logstashNodes, feedEndpoint,
                                   Optional.ofNullable(keyStoreOptions), Optional.ofNullable(trustStoreOptions));
        }
    }
}
