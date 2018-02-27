// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.component;

import com.google.common.base.Strings;
import com.yahoo.config.provision.NodeType;
import com.yahoo.vespa.athenz.api.AthenzIdentity;
import com.yahoo.vespa.athenz.api.AthenzService;
import com.yahoo.vespa.defaults.Defaults;
import com.yahoo.vespa.hosted.dockerapi.ContainerName;
import com.yahoo.vespa.hosted.node.admin.config.ConfigServerConfig;
import com.yahoo.vespa.hosted.node.admin.util.InetAddressResolver;
import com.yahoo.vespa.hosted.node.admin.util.KeyStoreOptions;

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
import java.util.Objects;
import java.util.Optional;
import java.util.TimeZone;
import java.util.stream.Collectors;

/**
 * Various utilities for getting values from node-admin's environment. Immutable.
 *
 * @author Øyvind Bakksjø
 * @author hmusum
 */
public class Environment {
    private static final DateFormat filenameFormatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");
    public static final String APPLICATION_STORAGE_CLEANUP_PATH_PREFIX = "cleanup_";

    private static final String ENVIRONMENT = "ENVIRONMENT";
    private static final String REGION = "REGION";
    private static final String SYSTEM = "SYSTEM";
    private static final String LOGSTASH_NODES = "LOGSTASH_NODES";
    private static final String COREDUMP_FEED_ENDPOINT = "COREDUMP_FEED_ENDPOINT";

    private final List<String> configServerHostNames;
    private final List<URI> configServerURIs;
    private final String environment;
    private final String region;
    private final String system;
    private final String parentHostHostname;
    private final InetAddressResolver inetAddressResolver;
    private final PathResolver pathResolver;
    private final List<String> logstashNodes;
    private final Optional<String> feedEndpoint;
    private final Optional<KeyStoreOptions> keyStoreOptions;
    private final Optional<KeyStoreOptions> trustStoreOptions;
    private final Optional<AthenzIdentity> athenzIdentity;
    private final NodeType nodeType;

    static {
        filenameFormatter.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    public Environment(ConfigServerConfig configServerConfig) {
        this(configServerConfig,
             // TODO: Are these three ever set? Does not look like they are. How can this work then?
             getEnvironmentVariable(ENVIRONMENT),
             getEnvironmentVariable(REGION),
             getEnvironmentVariable(SYSTEM),

             new PathResolver(),
             Optional.of(getEnvironmentVariable(COREDUMP_FEED_ENDPOINT)),
             NodeType.host);
    }

    public Environment(ConfigServerConfig configServerConfig,
                       String hostedEnvironment,
                       String hostedRegion,
                       String hostedSystem,
                       PathResolver pathResolver,
                       Optional<String> coreDumpFeedEndpoint,
                       NodeType nodeType) {
        this(configServerConfig,
                hostedEnvironment,
                hostedRegion,
                hostedSystem,
                Defaults.getDefaults().vespaHostname(),
                new InetAddressResolver(),
                pathResolver,
                getLogstashNodesFromEnvironment(),
                coreDumpFeedEndpoint,

                createKeyStoreOptions(
                        configServerConfig.keyStoreConfig().path(),
                        configServerConfig.keyStoreConfig().password().toCharArray(),
                        configServerConfig.keyStoreConfig().type().name(),
                        "BC"),
                createKeyStoreOptions(
                        configServerConfig.trustStoreConfig().path(),
                        configServerConfig.trustStoreConfig().password().toCharArray(),
                        configServerConfig.trustStoreConfig().type().name(),
                        null),
                createAthenzIdentity(
                        configServerConfig.athenzDomain(),
                        configServerConfig.serviceName()),
             nodeType
        );
    }

    public Environment(ConfigServerConfig configServerConfig,
                       String environment,
                       String region,
                       String system,
                       String parentHostHostname,
                       InetAddressResolver inetAddressResolver,
                       PathResolver pathResolver,
                       List<String> logstashNodes,
                       Optional<String> feedEndpoint,
                       Optional<KeyStoreOptions> keyStoreOptions,
                       Optional<KeyStoreOptions> trustStoreOptions,
                       Optional<AthenzIdentity> athenzIdentity,
                       NodeType nodeType) {
        this.configServerHostNames = configServerConfig.hosts();
        this.configServerURIs = createConfigServerUris(
                configServerConfig.scheme(),
                configServerConfig.hosts(),
                configServerConfig.port());
        this.environment = environment;
        this.region = region;
        this.system = system;
        this.parentHostHostname = parentHostHostname;
        this.inetAddressResolver = inetAddressResolver;
        this.pathResolver = pathResolver;
        this.logstashNodes = logstashNodes;
        this.feedEndpoint = feedEndpoint;
        this.keyStoreOptions = keyStoreOptions;
        this.trustStoreOptions = trustStoreOptions;
        this.athenzIdentity = athenzIdentity;
        this.nodeType = nodeType;
    }

    public List<String> getConfigServerHostNames() { return configServerHostNames; }

    public List<URI> getConfigServerUris() { return configServerURIs; }

    public String getEnvironment() { return environment; }

    public String getRegion() {
        return region;
    }

    public String getSystem() {
        return system;
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

    public static List<URI> createConfigServerUris(String scheme, List<String> configServerHosts, int port) {
        return configServerHosts.stream()
                .map(hostname -> URI.create(scheme + "://" + hostname + ":" + port))
                .collect(Collectors.toList());
    }

    private static List<String> getLogstashNodesFromEnvironment() {
        String logstashNodes = System.getenv(LOGSTASH_NODES);
        if (Strings.isNullOrEmpty(logstashNodes)) {
            return Collections.emptyList();
        }
        return Arrays.asList(logstashNodes.split("[,\\s]+"));
    }

    private static Optional<KeyStoreOptions> createKeyStoreOptions(String pathToKeyStore, char[] password, String type, String provider) {
        return Optional.ofNullable(pathToKeyStore)
                .filter(path -> !Strings.isNullOrEmpty(path))
                .map(path -> new KeyStoreOptions(Paths.get(path), password, type, provider));
    }

    private static Optional<AthenzIdentity> createAthenzIdentity(String athenzDomain, String serviceName) {
        if (Strings.isNullOrEmpty(athenzDomain) || Strings.isNullOrEmpty(serviceName)) return Optional.empty();
        return Optional.of(new AthenzService(athenzDomain, serviceName));
    }

    public InetAddress getInetAddressForHost(String hostname) throws UnknownHostException {
        return inetAddressResolver.getInetAddressForHost(hostname);
    }

    public PathResolver getPathResolver() {
        return pathResolver;
    }

    public Optional<String> getCoredumpFeedEndpoint() {
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
     * @param pathInNode absolute path in that container
     * @return the absolute path in node admin container pointing at the same inode
     */
    public Path pathInNodeAdminFromPathInNode(ContainerName containerName, Path pathInNode) {
        if (! pathInNode.isAbsolute()) {
            throw new IllegalArgumentException("The specified path in node was not absolute: " + pathInNode);
        }

        return pathResolver.getApplicationStoragePathForNodeAdmin()
                .resolve(containerName.asString())
                .resolve(PathResolver.ROOT.relativize(pathInNode));
    }

    /**
     * Translates an absolute path in node agent container to an absolute path in host.
     * @param containerName name of the node agent container
     * @param pathInNode absolute path in that container
     * @return the absolute path in host pointing at the same inode
     */
    public Path pathInHostFromPathInNode(ContainerName containerName, Path pathInNode) {
        if (! pathInNode.isAbsolute()) {
            throw new IllegalArgumentException("The specified path in node was not absolute: " + pathInNode);
        }

        return pathResolver.getApplicationStoragePathForHost()
                .resolve(containerName.asString())
                .resolve(PathResolver.ROOT.relativize(pathInNode));
    }

    public Path pathInNodeUnderVespaHome(String relativePath) {
        return pathResolver.getVespaHomePathForContainer()
                .resolve(relativePath);
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

    public Optional<AthenzIdentity> getAthenzIdentity() {
        return athenzIdentity;
    }

    public NodeType getNodeType() { return nodeType; }

    public static class Builder {
        ConfigServerConfig configServerConfig = new ConfigServerConfig(new ConfigServerConfig.Builder());
        private String environment;
        private String region;
        private String system;
        private String parentHostHostname;
        private InetAddressResolver inetAddressResolver;
        private PathResolver pathResolver;
        private List<String> logstashNodes = Collections.emptyList();
        private Optional<String> feedEndpoint = Optional.empty();
        private KeyStoreOptions keyStoreOptions;
        private KeyStoreOptions trustStoreOptions;
        private AthenzIdentity athenzIdentity;
        private NodeType nodeType = NodeType.tenant;

        public Builder configServerConfig(ConfigServerConfig configServerConfig) {
            this.configServerConfig = configServerConfig;
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

        public Builder system(String system) {
            this.system = system;
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
            this.feedEndpoint = Optional.of(feedEndpoint);
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

        public Builder athenzIdentity(AthenzIdentity athenzIdentity) {
            this.athenzIdentity = athenzIdentity;
            return this;
        }

        public Builder nodeType(NodeType nodeType) {
            this.nodeType = nodeType;
            return this;
        }

        public Environment build() {
            Objects.requireNonNull(environment, "environment cannot be null");
            Objects.requireNonNull(region, "region cannot be null");
            Objects.requireNonNull(system, "system cannot be null");
            return new Environment(configServerConfig,
                                   environment,
                                   region,
                                   system,
                                   parentHostHostname,
                                   Optional.ofNullable(inetAddressResolver).orElseGet(InetAddressResolver::new),
                                   Optional.ofNullable(pathResolver).orElseGet(PathResolver::new),
                                   logstashNodes,
                                   feedEndpoint,
                                   Optional.ofNullable(keyStoreOptions),
                                   Optional.ofNullable(trustStoreOptions),
                                   Optional.ofNullable(athenzIdentity),
                                   nodeType);
        }
    }
}
