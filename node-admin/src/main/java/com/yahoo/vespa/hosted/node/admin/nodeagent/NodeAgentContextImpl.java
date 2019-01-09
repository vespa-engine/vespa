package com.yahoo.vespa.hosted.node.admin.nodeagent;

import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.vespa.athenz.api.AthenzService;
import com.yahoo.vespa.hosted.dockerapi.ContainerName;
import com.yahoo.vespa.hosted.node.admin.component.ZoneId;
import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.NodeSpec;
import com.yahoo.vespa.hosted.node.admin.docker.DockerNetworking;
import com.yahoo.vespa.hosted.provision.Node;

import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author freva
 */
public class NodeAgentContextImpl implements NodeAgentContext {
    private static final Path ROOT = Paths.get("/");

    private final String logPrefix;
    private final NodeSpec node;
    private final ContainerName containerName;
    private final AthenzService identity;
    private final DockerNetworking dockerNetworking;
    private final ZoneId zoneId;
    private final Path pathToNodeRootOnHost;
    private final Path pathToVespaHome;
    private final String vespaUser;
    private final String vespaUserOnHost;

    public NodeAgentContextImpl(NodeSpec node, AthenzService identity,
                                DockerNetworking dockerNetworking, ZoneId zoneId,
                                Path pathToContainerStorage, Path pathToVespaHome,
                                String vespaUser, String vespaUserOnHost) {
        this.node = Objects.requireNonNull(node);
        this.containerName = ContainerName.fromHostname(node.getHostname());
        this.identity = Objects.requireNonNull(identity);
        this.dockerNetworking = Objects.requireNonNull(dockerNetworking);
        this.zoneId = Objects.requireNonNull(zoneId);
        this.pathToNodeRootOnHost = Objects.requireNonNull(pathToContainerStorage).resolve(containerName.asString());
        this.pathToVespaHome = Objects.requireNonNull(pathToVespaHome);
        this.logPrefix = containerName.asString() + ": ";
        this.vespaUser = vespaUser;
        this.vespaUserOnHost = vespaUserOnHost;
    }

    @Override
    public NodeSpec node() {
        return node;
    }

    @Override
    public ContainerName containerName() {
        return containerName;
    }

    @Override
    public AthenzService identity() {
        return identity;
    }

    @Override
    public DockerNetworking dockerNetworking() {
        return dockerNetworking;
    }

    @Override
    public ZoneId zoneId() {
        return zoneId;
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
    public Path pathOnHostFromPathInNode(Path pathInNode) {
        if (! pathInNode.isAbsolute())
            throw new IllegalArgumentException("Expected an absolute path in the container, got: " + pathInNode);

        return pathToNodeRootOnHost.resolve(ROOT.relativize(pathInNode).toString());
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
    public Path pathInNodeUnderVespaHome(Path relativePath) {
        if (relativePath.isAbsolute())
            throw new IllegalArgumentException("Expected a relative path to the Vespa home, got: " + relativePath);

        return pathToVespaHome.resolve(relativePath);
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
                ", containerName=" + containerName +
                ", identity=" + identity +
                ", dockerNetworking=" + dockerNetworking +
                ", zoneId=" + zoneId +
                ", pathToNodeRootOnHost=" + pathToNodeRootOnHost +
                ", pathToVespaHome=" + pathToVespaHome +
                ", vespaUser='" + vespaUser + '\'' +
                ", vespaUserOnHost='" + vespaUserOnHost + '\'' +
                '}';
    }

    /** For testing only! */
    public static class Builder {
        private NodeSpec.Builder nodeSpecBuilder = new NodeSpec.Builder();
        private AthenzService identity;
        private DockerNetworking dockerNetworking;
        private ZoneId zoneId;
        private Path pathToContainerStorage;
        private Path pathToVespaHome;
        private String vespaUser;
        private String vespaUserOnHost;

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
                    .state(Node.State.active)
                    .nodeType(NodeType.tenant)
                    .flavor("d-2-8-50");
        }

        public Builder nodeType(NodeType nodeType) {
            this.nodeSpecBuilder.nodeType(nodeType);
            return this;
        }

        public Builder identity(AthenzService identity) {
            this.identity = identity;
            return this;
        }

        public Builder dockerNetworking(DockerNetworking dockerNetworking) {
            this.dockerNetworking = dockerNetworking;
            return this;
        }

        public Builder zoneId(ZoneId zoneId) {
            this.zoneId = zoneId;
            return this;
        }

        public Builder pathToContainerStorage(Path pathToContainerStorage) {
            this.pathToContainerStorage = pathToContainerStorage;
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

        public Builder fileSystem(FileSystem fileSystem) {
            return pathToContainerStorage(fileSystem.getPath("/home/docker"));
        }

        public NodeAgentContextImpl build() {
            return new NodeAgentContextImpl(
                    nodeSpecBuilder.build(),
                    Optional.ofNullable(identity).orElseGet(() -> new AthenzService("domain", "service")),
                    Optional.ofNullable(dockerNetworking).orElse(DockerNetworking.HOST_NETWORK),
                    Optional.ofNullable(zoneId).orElseGet(() -> new ZoneId(SystemName.dev, Environment.dev, RegionName.defaultName())),
                    Optional.ofNullable(pathToContainerStorage).orElseGet(() -> Paths.get("/home/docker")),
                    Optional.ofNullable(pathToVespaHome).orElseGet(() -> Paths.get("/opt/vespa")),
                    Optional.ofNullable(vespaUser).orElse("vespa"),
                    Optional.ofNullable(vespaUserOnHost).orElse("container_vespa"));
        }
    }
}
