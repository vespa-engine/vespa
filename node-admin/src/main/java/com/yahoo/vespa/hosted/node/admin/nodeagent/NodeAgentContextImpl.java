// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.nodeagent;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.CloudName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.zone.ZoneApi;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.athenz.api.AthenzIdentity;
import com.yahoo.vespa.athenz.api.AthenzService;
import com.yahoo.vespa.flags.FetchVector;
import com.yahoo.vespa.flags.FlagSource;
import com.yahoo.vespa.flags.InMemoryFlagSource;
import com.yahoo.vespa.flags.PermanentFlags;
import com.yahoo.vespa.hosted.dockerapi.ContainerName;
import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.Acl;
import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.NodeSpec;
import com.yahoo.vespa.hosted.node.admin.docker.ContainerNetworkMode;

import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.ProviderMismatchException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author freva
 */
public class NodeAgentContextImpl implements NodeAgentContext {

    private final String logPrefix;
    private final NodeSpec node;
    private final Acl acl;
    private final ContainerName containerName;
    private final AthenzIdentity identity;
    private final ContainerNetworkMode containerNetworkMode;
    private final ZoneApi zone;
    private final FileSystem fileSystem;
    private final Path pathToNodeRootOnHost;
    private final Path pathToVespaHome;
    private final String vespaUser;
    private final String vespaUserOnHost;
    private final double cpuSpeedup;
    private final Set<NodeAgentTask> disabledNodeAgentTasks;
    private final Optional<ApplicationId> hostExclusiveTo;

    public NodeAgentContextImpl(NodeSpec node, Acl acl, AthenzIdentity identity,
                                ContainerNetworkMode containerNetworkMode, ZoneApi zone,
                                FileSystem fileSystem, FlagSource flagSource,
                                Path pathToContainerStorage, Path pathToVespaHome,
                                String vespaUser, String vespaUserOnHost, double cpuSpeedup,
                                Optional<ApplicationId> hostExclusiveTo) {
        if (cpuSpeedup <= 0)
            throw new IllegalArgumentException("cpuSpeedUp must be positive, was: " + cpuSpeedup);

        this.node = Objects.requireNonNull(node);
        this.acl = Objects.requireNonNull(acl);
        this.containerName = ContainerName.fromHostname(node.hostname());
        this.identity = Objects.requireNonNull(identity);
        this.containerNetworkMode = Objects.requireNonNull(containerNetworkMode);
        this.zone = Objects.requireNonNull(zone);
        this.fileSystem = Objects.requireNonNull(fileSystem);
        this.pathToNodeRootOnHost = requireValidPath(pathToContainerStorage).resolve(containerName.asString());
        this.pathToVespaHome = requireValidPath(pathToVespaHome);
        this.logPrefix = containerName.asString() + ": ";
        this.vespaUser = vespaUser;
        this.vespaUserOnHost = vespaUserOnHost;
        this.cpuSpeedup = cpuSpeedup;
        this.disabledNodeAgentTasks = NodeAgentTask.fromString(
                PermanentFlags.DISABLED_HOST_ADMIN_TASKS.bindTo(flagSource).with(FetchVector.Dimension.HOSTNAME, node.hostname()).value());
        this.hostExclusiveTo = hostExclusiveTo;
    }

    @Override
    public NodeSpec node() {
        return node;
    }

    @Override
    public Acl acl() {
        return acl;
    }

    @Override
    public ContainerName containerName() {
        return containerName;
    }

    @Override
    public AthenzIdentity identity() {
        return identity;
    }

    @Override
    public ContainerNetworkMode networkMode() {
        return containerNetworkMode;
    }

    @Override
    public ZoneApi zone() {
        return zone;
    }

    @Override
    public String vespaUser() {
        return vespaUser;
    }

    @Override
    public String vespaUserOnHost() {
        return vespaUserOnHost;
    }

    @Override
    public boolean isDisabled(NodeAgentTask task) {
        return disabledNodeAgentTasks.contains(task);
    }

    @Override
    public double vcpuOnThisHost() {
        return node.vcpu() / cpuSpeedup;
    }

    @Override
    public FileSystem fileSystem() {
        return fileSystem;
    }

    @Override
    public Path pathOnHostFromPathInNode(Path pathInNode) {
        requireValidPath(pathInNode);

        if (! pathInNode.isAbsolute())
            throw new IllegalArgumentException("Expected an absolute path in the container, got: " + pathInNode);

        return pathToNodeRootOnHost.resolve(pathInNode.getRoot().relativize(pathInNode));
    }

    @Override
    public Path pathInNodeFromPathOnHost(Path pathOnHost) {
        requireValidPath(pathOnHost);

        if (! pathOnHost.isAbsolute())
            throw new IllegalArgumentException("Expected an absolute path on the host, got: " + pathOnHost);

        if (!pathOnHost.startsWith(pathToNodeRootOnHost))
            throw new IllegalArgumentException("Path " + pathOnHost + " does not exist in the container");

        return pathOnHost.getRoot().resolve(pathToNodeRootOnHost.relativize(pathOnHost));
    }

    @Override
    public Path pathInNodeUnderVespaHome(Path relativePath) {
        requireValidPath(relativePath);

        if (relativePath.isAbsolute())
            throw new IllegalArgumentException("Expected a relative path to the Vespa home, got: " + relativePath);

        return pathToVespaHome.resolve(relativePath);
    }

    @Override
    public Optional<ApplicationId> hostExclusiveTo() {
        return hostExclusiveTo;
    }

    @Override
    public void recordSystemModification(Logger logger, String message) {
        log(logger, message);
    }

    @Override
    public void log(Logger logger, Level level, String message) {
        logger.log(level, logPrefix + message);
    }

    @Override
    public void log(Logger logger, Level level, String message, Throwable throwable) {
        logger.log(level, logPrefix + message, throwable);
    }

    @Override
    public String toString() {
        return "NodeAgentContextImpl{" +
               "node=" + node +
               ", acl=" + acl +
               ", containerName=" + containerName +
               ", identity=" + identity +
               ", dockerNetworking=" + containerNetworkMode +
               ", zone=" + zone +
               ", pathToNodeRootOnHost=" + pathToNodeRootOnHost +
               ", pathToVespaHome=" + pathToVespaHome +
               ", vespaUser='" + vespaUser + '\'' +
               ", vespaUserOnHost='" + vespaUserOnHost + '\'' +
               ", hostExclusiveTo='" + hostExclusiveTo + '\'' +
               '}';
    }

    private Path requireValidPath(Path path) {
        Objects.requireNonNull(path);

        Objects.requireNonNull(fileSystem); // to allow this method to be used in constructor.
        if (!path.getFileSystem().provider().equals(fileSystem.provider())) {
            throw new ProviderMismatchException("Expected file system provider " + fileSystem.provider() +
                    " but " + path + " had " + path.getFileSystem().provider());
        }

        return path;
    }

    /** For testing only! */
    public static class Builder {
        private NodeSpec.Builder nodeSpecBuilder;
        private Acl acl;
        private AthenzIdentity identity;
        private ContainerNetworkMode containerNetworkMode;
        private ZoneApi zone;
        private String vespaUser;
        private String vespaUserOnHost;
        private FileSystem fileSystem = FileSystems.getDefault();
        private FlagSource flagSource;
        private double cpuSpeedUp = 1;
        private Path containerStorage;
        private Optional<ApplicationId> hostExclusiveTo = Optional.empty();

        public Builder(NodeSpec node) {
            this.nodeSpecBuilder = new NodeSpec.Builder(node);
        }

        /**
         * Creates a NodeAgentContext.Builder with a NodeSpec that has the given hostname and some
         * reasonable values for the remaining required NodeSpec fields. Use {@link #Builder(NodeSpec)}
         * if you want to control the entire NodeSpec.
         */
        public Builder(String hostname) {
            this.nodeSpecBuilder = NodeSpec.Builder.testSpec(hostname);
        }

        public Builder nodeSpecBuilder(Function<NodeSpec.Builder, NodeSpec.Builder> nodeSpecBuilderModifier) {
            this.nodeSpecBuilder = nodeSpecBuilderModifier.apply(nodeSpecBuilder);
            return this;
        }

        public Builder acl(Acl acl) {
            this.acl = acl;
            return this;
        }

        public Builder identity(AthenzIdentity identity) {
            this.identity = identity;
            return this;
        }

        public Builder networkMode(ContainerNetworkMode containerNetworkMode) {
            this.containerNetworkMode = containerNetworkMode;
            return this;
        }

        public Builder zone(ZoneApi zone) {
            this.zone = zone;
            return this;
        }

        public Builder vespaUser(String vespaUser) {
            this.vespaUser = vespaUser;
            return this;
        }

        public Builder vespaUserOnHost(String vespaUserOnHost) {
            this.vespaUserOnHost = vespaUserOnHost;
            return this;
        }

        /** Sets the file system to use for paths. */
        public Builder fileSystem(FileSystem fileSystem) {
            this.fileSystem = fileSystem;
            return this;
        }

        public Builder flagSource(FlagSource flagSource) {
            this.flagSource = flagSource;
            return this;
        }

        public Builder cpuSpeedUp(double cpuSpeedUp) {
            this.cpuSpeedUp = cpuSpeedUp;
            return this;
        }

        public Builder containerStorage(Path path) {
            this.containerStorage = path;
            return this;
        }

        public Builder hostExclusiveTo(ApplicationId applicationId) {
            this.hostExclusiveTo = Optional.ofNullable(applicationId);
            return this;
        }

        public NodeAgentContextImpl build() {
            return new NodeAgentContextImpl(
                    nodeSpecBuilder.build(),
                    Optional.ofNullable(acl).orElse(Acl.EMPTY),
                    Optional.ofNullable(identity).orElseGet(() -> new AthenzService("domain", "service")),
                    Optional.ofNullable(containerNetworkMode).orElse(ContainerNetworkMode.HOST_NETWORK),
                    Optional.ofNullable(zone).orElseGet(() -> new ZoneApi() {
                        @Override
                        public SystemName getSystemName() {
                            return SystemName.defaultSystem();
                        }

                        @Override
                        public ZoneId getId() {
                            return ZoneId.defaultId();
                        }

                        @Override
                        public CloudName getCloudName() {
                            return CloudName.defaultName();
                        }

                        @Override
                        public String getCloudNativeRegionName() {
                            return getId().region().value();
                        }
                    }),
                    fileSystem,
                    Optional.ofNullable(flagSource).orElseGet(InMemoryFlagSource::new),
                    Optional.ofNullable(containerStorage).orElseGet(() -> fileSystem.getPath("/home/docker/container-storage")),
                    fileSystem.getPath("/opt/vespa"),
                    Optional.ofNullable(vespaUser).orElse("vespa"),
                    Optional.ofNullable(vespaUserOnHost).orElse("container_vespa"),
                    cpuSpeedUp, hostExclusiveTo);
        }
    }
}
