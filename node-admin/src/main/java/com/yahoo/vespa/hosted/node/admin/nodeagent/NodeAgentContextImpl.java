// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.Acl;
import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.NodeSpec;
import com.yahoo.vespa.hosted.node.admin.container.ContainerName;
import com.yahoo.vespa.hosted.node.admin.container.ContainerNetworkMode;
import com.yahoo.vespa.hosted.node.admin.task.util.file.UnixUser;
import com.yahoo.vespa.hosted.node.admin.task.util.fs.ContainerFileSystem;

import java.nio.file.FileSystem;
import java.nio.file.Path;
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
    private final UserScope userScope;
    private final PathScope pathScope;
    private final double cpuSpeedup;
    private final Set<NodeAgentTask> disabledNodeAgentTasks;
    private final Optional<ApplicationId> hostExclusiveTo;

    public NodeAgentContextImpl(NodeSpec node, Acl acl, AthenzIdentity identity,
                                ContainerNetworkMode containerNetworkMode, ZoneApi zone,
                                FlagSource flagSource, UserScope userScope, PathScope pathScope,
                                double cpuSpeedup, Optional<ApplicationId> hostExclusiveTo) {
        if (cpuSpeedup <= 0)
            throw new IllegalArgumentException("cpuSpeedUp must be positive, was: " + cpuSpeedup);

        this.node = Objects.requireNonNull(node);
        this.acl = Objects.requireNonNull(acl);
        this.containerName = ContainerName.fromHostname(node.hostname());
        this.identity = Objects.requireNonNull(identity);
        this.containerNetworkMode = Objects.requireNonNull(containerNetworkMode);
        this.zone = Objects.requireNonNull(zone);
        this.userScope = Objects.requireNonNull(userScope);
        this.pathScope = Objects.requireNonNull(pathScope);
        this.logPrefix = containerName.asString() + ": ";
        this.cpuSpeedup = cpuSpeedup;
        this.disabledNodeAgentTasks = NodeAgentTask.fromString(
                PermanentFlags.DISABLED_HOST_ADMIN_TASKS.bindTo(flagSource)
                        .with(FetchVector.Dimension.HOSTNAME, node.hostname())
                        .with(FetchVector.Dimension.NODE_TYPE, node.type().name()).value());
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
    public UserScope users() {
        return userScope;
    }

    @Override
    public PathScope paths() {
        return pathScope;
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

    public static NodeAgentContextImpl.Builder builder(NodeSpec node) {
        return new Builder(new NodeSpec.Builder(node));
    }

    /**
     * Creates a NodeAgentContext.Builder with a NodeSpec that has the given hostname and some
     * reasonable values for the remaining required NodeSpec fields. Use {@link #builder(NodeSpec)}
     * if you want to control the entire NodeSpec.
     */
    public static NodeAgentContextImpl.Builder builder(String hostname) {
        return new Builder(NodeSpec.Builder.testSpec(hostname));
    }

    /** For testing only! */
    public static class Builder {
        private static final Path DEFAULT_CONTAINER_STORAGE = Path.of("/data/vespa/storage");

        private NodeSpec.Builder nodeSpecBuilder;
        private Acl acl;
        private AthenzIdentity identity;
        private ContainerNetworkMode containerNetworkMode;
        private ZoneApi zone;
        private UserNamespace userNamespace;
        private UnixUser vespaUser;
        private Path containerStorage;
        private FlagSource flagSource;
        private double cpuSpeedUp = 1;
        private Optional<ApplicationId> hostExclusiveTo = Optional.empty();

        private Builder(NodeSpec.Builder nodeSpecBuilder) {
            this.nodeSpecBuilder = nodeSpecBuilder;
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

        public Builder userNamespace(UserNamespace userNamespace) {
            this.userNamespace = userNamespace;
            return this;
        }

        public Builder vespaUser(UnixUser vespaUser) {
            this.vespaUser = vespaUser;
            return this;
        }


        /** Sets the file system to use for paths. */
        public Builder fileSystem(FileSystem fileSystem) {
            return containerStorage(fileSystem.getPath(DEFAULT_CONTAINER_STORAGE.toString()));
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
            Objects.requireNonNull(containerStorage, "Must set one of containerStorage or fileSystem");

            UserScope userScope = UserScope.create(
                    Optional.ofNullable(vespaUser).orElseGet(() -> new UnixUser("vespa", 1000, "vespa", 100)),
                    Optional.ofNullable(userNamespace).orElseGet(() -> new UserNamespace(100000, 100000, 100000)));
            ContainerFileSystem containerFs = ContainerFileSystem.create(containerStorage
                    .resolve(nodeSpecBuilder.hostname().split("\\.")[0]), userScope);
            containerFs.createRoot();

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
                            return CloudName.DEFAULT;
                        }

                        @Override
                        public String getCloudNativeRegionName() {
                            return getId().region().value();
                        }
                    }),
                    Optional.ofNullable(flagSource).orElseGet(InMemoryFlagSource::new),
                    userScope,
                    new PathScope(containerFs, "/opt/vespa"),
                    cpuSpeedUp, hostExclusiveTo);
        }
    }
}
