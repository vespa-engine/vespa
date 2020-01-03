package com.yahoo.vespa.hosted.node.admin.nodeagent;

import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.zone.ZoneApi;
import com.yahoo.vespa.athenz.api.AthenzIdentity;
import com.yahoo.vespa.hosted.dockerapi.ContainerName;
import com.yahoo.vespa.hosted.node.admin.component.TaskContext;
import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.Acl;
import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.NodeSpec;
import com.yahoo.vespa.hosted.node.admin.docker.DockerNetworking;

import java.nio.file.Path;

public interface NodeAgentContext extends TaskContext {

    /** @return node specification from node-repository */
    NodeSpec node();

    /** @return node ACL from node-repository */
    Acl acl();

    /** @return name of the docker container this context applies to */
    ContainerName containerName();

    /** @return hostname of the docker container this context applies to */
    default HostName hostname() {
        return HostName.from(node().hostname());
    }

    default NodeType nodeType() {
        return node().type();
    }

    AthenzIdentity identity();

    DockerNetworking dockerNetworking();

    ZoneApi zone();

    String vespaUser();

    String vespaUserOnHost();

    /**
     * The vcpu value in NodeSpec is multiplied by the speedup factor per cpu core compared to a historical baseline
     * for a particular cpu generation of the host (see flavors.def cpuSpeedup).
     *
     * @return node vcpu without the cpu speedup factor.
     */
    double unscaledVcpu();

    /**
     * This method is the inverse of {@link #pathInNodeFromPathOnHost(Path)}}
     *
     * @param pathInNode absolute path in the container
     * @return the absolute path on host pointing at the same inode
     */
    Path pathOnHostFromPathInNode(Path pathInNode);

    /** @see #pathOnHostFromPathInNode(Path) */
    Path pathOnHostFromPathInNode(String pathInNode);

    /**
     * This method is the inverse of {@link #pathOnHostFromPathInNode(Path)}
     *
     * @param pathOnHost absolute path on host
     * @return the absolute path in the container pointing at the same inode
     */
    Path pathInNodeFromPathOnHost(Path pathOnHost);

    /** @see #pathOnHostFromPathInNode(Path) */
    Path pathInNodeFromPathOnHost(String pathOnHost);


    /**
     * @param relativePath relative path under Vespa home in container
     * @return the absolute path under Vespa home in the container
     */
    Path pathInNodeUnderVespaHome(Path relativePath);

    /** @see #pathInNodeUnderVespaHome(Path) */
    Path pathInNodeUnderVespaHome(String relativePath);
}
