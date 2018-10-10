package com.yahoo.vespa.hosted.node.admin.nodeagent;

import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.NodeType;
import com.yahoo.vespa.athenz.api.AthenzService;
import com.yahoo.vespa.hosted.dockerapi.ContainerName;
import com.yahoo.vespa.hosted.node.admin.component.TaskContext;

import java.nio.file.Path;
import java.nio.file.Paths;

public interface NodeAgentContext extends TaskContext {

    ContainerName containerName();

    HostName hostname();

    NodeType nodeType();

    AthenzService identity();

    /**
     * Translates an absolute path in container to an absolute path in host.
     *
     * @param pathInNode absolute path in the container
     * @return the absolute path on host pointing at the same inode
     */
    Path pathOnHostFromPathInNode(Path pathInNode);

    default Path pathOnHostFromPathInNode(String pathInNode) {
        return pathOnHostFromPathInNode(Paths.get(pathInNode));
    }

    /**
     * @param relativePath relative path under Vespa home in container
     * @return the absolute path under Vespa home in the container
     */
    Path pathInNodeUnderVespaHome(Path relativePath);

    default Path pathInNodeUnderVespaHome(String relativePath) {
        return pathInNodeUnderVespaHome(Paths.get(relativePath));
    }
}
