package com.yahoo.vespa.hosted.node.admin.nodeagent;

import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.NodeType;
import com.yahoo.vespa.athenz.api.AthenzService;
import com.yahoo.vespa.hosted.dockerapi.ContainerName;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author freva
 */
public class NodeAgentContextImpl implements NodeAgentContext {
    private static final Path ROOT = Paths.get("/");

    private final String logPrefix;
    private final ContainerName containerName;
    private final HostName hostName;
    private final NodeType nodeType;
    private final AthenzService identity;
    private final Path pathToNodeRootOnHost;
    private final Path pathToVespaHome;

    public NodeAgentContextImpl(String hostname, NodeType nodeType, AthenzService identity,
                                Path pathToContainerStorage, Path pathToVespaHome) {
        this.hostName = HostName.from(Objects.requireNonNull(hostname));
        this.containerName = ContainerName.fromHostname(hostname);
        this.nodeType = Objects.requireNonNull(nodeType);
        this.identity = Objects.requireNonNull(identity);
        this.pathToNodeRootOnHost = Objects.requireNonNull(pathToContainerStorage).resolve(containerName.asString());
        this.pathToVespaHome = Objects.requireNonNull(pathToVespaHome);
        this.logPrefix = containerName.asString() + ": ";
    }

    @Override
    public ContainerName containerName() {
        return containerName;
    }

    @Override
    public HostName hostname() {
        return hostName;
    }

    @Override
    public NodeType nodeType() {
        return nodeType;
    }

    @Override
    public AthenzService identity() {
        return identity;
    }

    @Override
    public Path pathOnHostFromPathInNode(Path pathInNode) {
        if (! pathInNode.isAbsolute())
            throw new IllegalArgumentException("Expected an absolute path in container, got: " + pathInNode);

        return pathToNodeRootOnHost.resolve(ROOT.relativize(pathInNode).toString());
    }

    @Override
    public Path pathInNodeUnderVespaHome(Path relativePath) {
        if (relativePath.isAbsolute())
            throw new IllegalArgumentException("Expected a relative path to Vespa home, got: " + relativePath);

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
}
