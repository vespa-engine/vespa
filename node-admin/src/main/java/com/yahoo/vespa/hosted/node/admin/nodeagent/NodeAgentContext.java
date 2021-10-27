// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.nodeagent;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.zone.ZoneApi;
import com.yahoo.vespa.athenz.api.AthenzIdentity;
import com.yahoo.vespa.hosted.node.admin.component.TaskContext;
import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.Acl;
import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.NodeSpec;
import com.yahoo.vespa.hosted.node.admin.container.ContainerName;
import com.yahoo.vespa.hosted.node.admin.container.ContainerNetworkMode;
import com.yahoo.vespa.hosted.node.admin.task.util.fs.ContainerPath;

import java.nio.file.Path;
import java.util.Optional;

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

    ContainerNetworkMode networkMode();

    ZoneApi zone();

    /** @return information about the Vespa user inside the container */
    VespaUser vespaUser();

    UserNamespace userNamespace();

    default boolean isDisabled(NodeAgentTask task) {
        return false;
    };

    /**
     * The vcpu value in NodeSpec is the number of vcpus required by the node on a fixed historical
     * baseline machine.  However the current host has a faster per-vcpu performance by a scale factor
     * (see flavors.def cpuSpeedup), and therefore do not need to set aside the full number of vcpus
     * to run the node.  This method returns that reduced number of vcpus.
     *
     * @return the vcpus required by the node on this host.
     */
    double vcpuOnThisHost();

    ContainerPath containerPath(String pathInNode);

    ContainerPath containerPathUnderVespaHome(String relativePath);

    ContainerPath containerPathFromPathOnHost(Path pathOnHost);

    Optional<ApplicationId> hostExclusiveTo();
}
