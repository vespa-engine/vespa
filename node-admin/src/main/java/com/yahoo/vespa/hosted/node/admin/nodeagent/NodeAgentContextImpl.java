package com.yahoo.vespa.hosted.node.admin.nodeagent;

import com.yahoo.config.provision.CloudName;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.zone.ZoneApi;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.athenz.api.AthenzIdentity;
import com.yahoo.vespa.athenz.api.AthenzService;
import com.yahoo.vespa.hosted.dockerapi.ContainerName;
import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.Acl;
import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.NodeSpec;
import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.NodeState;
import com.yahoo.vespa.hosted.node.admin.docker.DockerNetworking;

import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author freva
 */
public class NodeAgentContextImpl implements NodeAgentContext {
    private static final Path ROOT = Paths.get("/");

    private final String logPrefix;
    private final NodeSpec node;
    private final Acl acl;
    private final ContainerName containerName;
    private final AthenzIdentity identity;
    private final DockerNetworking dockerNetworking;
    private final ZoneApi zone;
    private final Path pathToNodeRootOnHost;
    private final Path pathToVespaHome;
    private final String vespaUser;
    private final String vespaUserOnHost;
    private final double cpuSpeedup;

    public NodeAgentContextImpl(NodeSpec node, Acl acl, AthenzIdentity identity,
                                DockerNetworking dockerNetworking, ZoneApi zone,
                                Path pathToContainerStorage, Path pathToVespaHome,
                                String vespaUser, String vespaUserOnHost, double cpuSpeedup) {
        if (cpuSpeedup <= 0)
            throw new IllegalArgumentException("cpuSpeedUp must be positive, was: " + cpuSpeedup);

        this.node = Objects.requireNonNull(node);
        this.acl = Objects.requireNonNull(acl);
        this.containerName = ContainerName.fromHostname(node.hostname());
        this.identity = Objects.requireNonNull(identity);
        this.dockerNetworking = Objects.requireNonNull(dockerNetworking);
        this.zone = Objects.requireNonNull(zone);
        this.pathToNodeRootOnHost = Objects.requireNonNull(pathToContainerStorage).resolve(containerName.asString());
        this.pathToVespaHome = Objects.requireNonNull(pathToVespaHome);
        this.logPrefix = containerName.asString() + ": ";
        this.vespaUser = vespaUser;
        this.vespaUserOnHost = vespaUserOnHost;
        this.cpuSpeedup = cpuSpeedup;
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
    public DockerNetworking dockerNetworking() {
        return dockerNetworking;
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
    public double normalizedVcpu() {
        return node.vcpu() / cpuSpeedup;
    }

    @Override
    public Path pathOnHostFromPathInNode(Path pathInNode) {
        if (! pathInNode.isAbsolute())
            throw new IllegalArgumentException("Expected an absolute path in the container, got: " + pathInNode);

        return pathToNodeRootOnHost.resolve(ROOT.relativize(pathInNode).toString());
    }

    @Override
    public Path pathOnHostFromPathInNode(String pathInNode) {
        // Ensure the path is on the proper FileSystem
        return pathOnHostFromPathInNode(ROOT.getFileSystem().getPath(pathInNode));
    }

    @Override
    public Path pathInNodeFromPathOnHost(Path pathOnHost) {
        if (! pathOnHost.isAbsolute())
            throw new IllegalArgumentException("Expected an absolute path on the host, got: " + pathOnHost);

        if (!pathOnHost.startsWith(pathToNodeRootOnHost))
            throw new IllegalArgumentException("Path " + pathOnHost + " does not exist in the container");

        return ROOT.resolve(pathToNodeRootOnHost.relativize(pathOnHost).toString());
    }

    @Override
    public Path pathInNodeFromPathOnHost(String pathOnHost) {
        // Ensure the path is on the proper FileSystem
        return pathInNodeFromPathOnHost(pathToNodeRootOnHost.getFileSystem().getPath(pathOnHost));
    }

    @Override
    public Path pathInNodeUnderVespaHome(Path relativePath) {
        if (relativePath.isAbsolute())
            throw new IllegalArgumentException("Expected a relative path to the Vespa home, got: " + relativePath);

        return pathToVespaHome.resolve(relativePath);
    }

    @Override
    public Path pathInNodeUnderVespaHome(String relativePath) {
        // Ensure the path is on the proper FileSystem
        return pathInNodeUnderVespaHome(pathToVespaHome.getFileSystem().getPath(relativePath));
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
                ", dockerNetworking=" + dockerNetworking +
                ", zone=" + zone +
                ", pathToNodeRootOnHost=" + pathToNodeRootOnHost +
                ", pathToVespaHome=" + pathToVespaHome +
                ", vespaUser='" + vespaUser + '\'' +
                ", vespaUserOnHost='" + vespaUserOnHost + '\'' +
                '}';
    }

    /** For testing only! */
    public static class Builder {
        private NodeSpec.Builder nodeSpecBuilder = new NodeSpec.Builder();
        private Acl acl;
        private AthenzIdentity identity;
        private DockerNetworking dockerNetworking;
        private ZoneApi zone;
        private Path pathToContainerStorage;
        private Path pathToVespaHome;
        private String vespaUser;
        private String vespaUserOnHost;
        private FileSystem fileSystem = FileSystems.getDefault();
        private double cpuSpeedUp = 1;

        public Builder(NodeSpec node) {
            this.nodeSpecBuilder = new NodeSpec.Builder(node);
        }

        /**
         * Creates a NodeAgentContext.Builder with a NodeSpec that has the given hostname and some
         * reasonable values for the remaining required NodeSpec fields. Use {@link #Builder(NodeSpec)}
         * if you want to control the entire NodeSpec.
         */
        public Builder(String hostname) {
            this.nodeSpecBuilder
                    .hostname(hostname)
                    .state(NodeState.active)
                    .type(NodeType.tenant)
                    .flavor("d-2-8-50");
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

        public Builder dockerNetworking(DockerNetworking dockerNetworking) {
            this.dockerNetworking = dockerNetworking;
            return this;
        }

        public Builder zone(ZoneApi zone) {
            this.zone = zone;
            return this;
        }

        public Builder pathToContainerStorageFromFileSystem(FileSystem fileSystem) {
            this.pathToContainerStorage = fileSystem.getPath("/home/docker");
            return this;
        }

        public Builder pathToVespaHome(Path pathToVespaHome) {
            this.pathToVespaHome = pathToVespaHome;
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

        /**
         * Sets the default file system to use for paths. May be overridden for each path,
         * e.g. {@link #pathToVespaHome(Path)}  pathToVespaHome()}.
         */
        public Builder fileSystem(FileSystem fileSystem) {
            this.fileSystem = fileSystem;
            return this;
        }

        public Builder cpuSpeedUp(double cpuSpeedUp) {
            this.cpuSpeedUp = cpuSpeedUp;
            return this;
        }

        public NodeAgentContextImpl build() {
            return new NodeAgentContextImpl(
                    nodeSpecBuilder.build(),
                    Optional.ofNullable(acl).orElse(Acl.EMPTY),
                    Optional.ofNullable(identity).orElseGet(() -> new AthenzService("domain", "service")),
                    Optional.ofNullable(dockerNetworking).orElse(DockerNetworking.HOST_NETWORK),
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
                    Optional.ofNullable(pathToContainerStorage).orElseGet(() -> fileSystem.getPath("/home/docker")),
                    Optional.ofNullable(pathToVespaHome).orElseGet(() -> fileSystem.getPath("/opt/vespa")),
                    Optional.ofNullable(vespaUser).orElse("vespa"),
                    Optional.ofNullable(vespaUserOnHost).orElse("container_vespa"),
                    cpuSpeedUp);
        }
    }
}
