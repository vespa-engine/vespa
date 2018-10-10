// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.component;

import com.yahoo.config.provision.NodeType;
import com.yahoo.vespa.athenz.api.AthenzService;
import com.yahoo.vespa.hosted.dockerapi.ContainerName;
import com.yahoo.vespa.hosted.node.admin.config.ConfigServerConfig;
import com.yahoo.vespa.hosted.node.admin.docker.DockerNetworking;
import com.yahoo.vespa.hosted.node.admin.task.util.network.IPAddresses;
import com.yahoo.vespa.hosted.node.admin.task.util.network.IPAddressesImpl;

import java.net.URI;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Various utilities for getting values from node-admin's environment. Immutable.
 *
 * @author Øyvind Bakksjø
 * @author hmusum
 */
public class Environment {
    private static final DateTimeFormatter filenameFormatter = DateTimeFormatter
            .ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS").withZone(ZoneOffset.UTC);
    public static final String APPLICATION_STORAGE_CLEANUP_PATH_PREFIX = "cleanup_";

    private final ConfigServerInfo configServerInfo;
    private final String environment;
    private final String region;
    private final String system;
    private final String cloud;
    private final String parentHostHostname;
    private final IPAddresses ipAddresses;
    private final PathResolver pathResolver;
    private final List<String> logstashNodes;
    private final NodeType nodeType;
    private final ContainerEnvironmentResolver containerEnvironmentResolver;
    private final String certificateDnsSuffix;
    private final URI ztsUri;
    private final AthenzService nodeAthenzIdentity;
    private final boolean nodeAgentCertEnabled;
    private final Path trustStorePath;
    private final DockerNetworking dockerNetworking;

    private Environment(ConfigServerConfig configServerConfig,
                        Path trustStorePath,
                        String environment,
                        String region,
                        String system,
                        String cloud,
                        String parentHostHostname,
                        IPAddresses ipAddresses,
                        PathResolver pathResolver,
                        List<String> logstashNodes,
                        NodeType nodeType,
                        ContainerEnvironmentResolver containerEnvironmentResolver,
                        String certificateDnsSuffix,
                        URI ztsUri,
                        AthenzService nodeAthenzIdentity,
                        boolean nodeAgentCertEnabled,
                        DockerNetworking dockerNetworking) {
        Objects.requireNonNull(configServerConfig, "configServerConfig cannot be null");

        this.configServerInfo = new ConfigServerInfo(configServerConfig);
        this.environment = Objects.requireNonNull(environment, "environment cannot be null");;
        this.region = Objects.requireNonNull(region, "region cannot be null");;
        this.system = Objects.requireNonNull(system, "system cannot be null");;
        this.cloud = Objects.requireNonNull(cloud, "cloud cannot be null");
        this.parentHostHostname = parentHostHostname;
        this.ipAddresses = ipAddresses;
        this.pathResolver = pathResolver;
        this.logstashNodes = logstashNodes;
        this.nodeType = nodeType;
        this.containerEnvironmentResolver = containerEnvironmentResolver;
        this.certificateDnsSuffix = certificateDnsSuffix;
        this.ztsUri = ztsUri;
        this.nodeAthenzIdentity = nodeAthenzIdentity;
        this.nodeAgentCertEnabled = nodeAgentCertEnabled;
        this.trustStorePath = trustStorePath;
        this.dockerNetworking = Objects.requireNonNull(dockerNetworking, "dockerNetworking cannot be null");
    }

    public List<String> getConfigServerHostNames() { return configServerInfo.getConfigServerHostNames(); }

    public String getEnvironment() { return environment; }

    public String getRegion() {
        return region;
    }

    public String getSystem() {
        return system;
    }

    public String getCloud() { return cloud; }

    public String getParentHostHostname() {
        return parentHostHostname;
    }

    public String getZone() {
        return getEnvironment() + "." + getRegion();
    }

    public IPAddresses getIpAddresses() {
        return ipAddresses;
    }

    public PathResolver getPathResolver() {
        return pathResolver;
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
                .resolve("archive")
                .resolve(containerName.asString() + "_" + filenameFormatter.format(Instant.now()));
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

    public NodeType getNodeType() { return nodeType; }

    public ContainerEnvironmentResolver getContainerEnvironmentResolver() {
        return containerEnvironmentResolver;
    }

    public Path getTrustStorePath() {
        return trustStorePath;
    }

    public AthenzService getConfigserverAthenzIdentity() {
        return configServerInfo.getConfigServerIdentity();
    }

    public AthenzService getNodeAthenzIdentity() {
        return nodeAthenzIdentity;
    }

    public String getCertificateDnsSuffix() {
        return certificateDnsSuffix;
    }

    public URI getZtsUri() {
        return ztsUri;
    }

    public URI getConfigserverLoadBalancerEndpoint() {
        return configServerInfo.getLoadBalancerEndpoint();
    }

    public boolean isNodeAgentCertEnabled() {
        return nodeAgentCertEnabled;
    }

    public DockerNetworking getDockerNetworking() {
        return dockerNetworking;
    }

    public static class Builder {
        private ConfigServerConfig configServerConfig;
        private String environment;
        private String region;
        private String system;
        private String cloud;
        private String parentHostHostname;
        private IPAddresses ipAddresses;
        private PathResolver pathResolver;
        private List<String> logstashNodes = Collections.emptyList();
        private NodeType nodeType = NodeType.tenant;
        private ContainerEnvironmentResolver containerEnvironmentResolver;
        private String certificateDnsSuffix;
        private URI ztsUri;
        private AthenzService nodeAthenzIdentity;
        private boolean nodeAgentCertEnabled;
        private Path trustStorePath;
        private DockerNetworking dockerNetworking;

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

        public Builder cloud(String cloud) {
            this.cloud = cloud;
            return this;
        }

        public Builder parentHostHostname(String parentHostHostname) {
            this.parentHostHostname = parentHostHostname;
            return this;
        }

        public Builder ipAddresses(IPAddresses ipAddresses) {
            this.ipAddresses = ipAddresses;
            return this;
        }

        public Builder pathResolver(PathResolver pathResolver) {
            this.pathResolver = pathResolver;
            return this;
        }

        public Builder containerEnvironmentResolver(ContainerEnvironmentResolver containerEnvironmentResolver) {
            this.containerEnvironmentResolver = containerEnvironmentResolver;
            return this;
        }

        public Builder logstashNodes(List<String> hosts) {
            this.logstashNodes = hosts;
            return this;
        }

        public Builder nodeType(NodeType nodeType) {
            this.nodeType = nodeType;
            return this;
        }

        public Builder certificateDnsSuffix(String certificateDnsSuffix) {
            this.certificateDnsSuffix = certificateDnsSuffix;
            return this;
        }

        public Builder ztsUri(URI ztsUri) {
            this.ztsUri = ztsUri;
            return this;
        }

        public Builder nodeAthenzIdentity(AthenzService nodeAthenzIdentity) {
            this.nodeAthenzIdentity = nodeAthenzIdentity;
            return this;
        }

        public Builder enableNodeAgentCert(boolean nodeAgentCertEnabled) {
            this.nodeAgentCertEnabled = nodeAgentCertEnabled;
            return this;
        }

        public Builder trustStorePath(Path trustStorePath) {
            this.trustStorePath = trustStorePath;
            return this;
        }

        public Builder dockerNetworking(DockerNetworking dockerNetworking) {
            this.dockerNetworking = dockerNetworking;
            return this;
        }

        public Environment build() {
            return new Environment(configServerConfig,
                                   trustStorePath,
                                   environment,
                                   region,
                                   system,
                                   cloud,
                                   parentHostHostname,
                                   Optional.ofNullable(ipAddresses).orElseGet(IPAddressesImpl::new),
                                   Optional.ofNullable(pathResolver).orElseGet(PathResolver::new),
                                   logstashNodes,
                                   nodeType,
                                   Optional.ofNullable(containerEnvironmentResolver).orElseGet(DefaultContainerEnvironmentResolver::new),
                                   certificateDnsSuffix,
                                   ztsUri,
                                   nodeAthenzIdentity,
                                   nodeAgentCertEnabled,
                                   dockerNetworking);
        }
    }
}
