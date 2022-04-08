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

import java.util.Optional;

public interface NodeAgentContext extends TaskContext {

    /** @return node specification from node-repository */
    NodeSpec node();

    /** @return node ACL from node-repository */
    Acl acl();

    /** @return name of the linux container this context applies to */
    ContainerName containerName();

    /** @return hostname of the linux container this context applies to */
    default HostName hostname() {
        return HostName.of(node().hostname());
    }

    default NodeType nodeType() {
        return node().type();
    }

    AthenzIdentity identity();

    ContainerNetworkMode networkMode();

    ZoneApi zone();

    /** @return information about users/user namespace of the linux container this context applies to */
    UserScope users();

    /** @return methods to resolve paths within container's file system */
    PathScope paths();

    default boolean isDisabled(NodeAgentTask task) {
        return false;
    }

    /**
     * The vcpu value in NodeSpec is the number of vcpus required by the node on a fixed historical
     * baseline machine.  However the current host has a faster per-vcpu performance by a scale factor
     * (see flavors.def cpuSpeedup), and therefore do not need to set aside the full number of vcpus
     * to run the node.  This method returns that reduced number of vcpus.
     *
     * @return the vcpus required by the node on this host.
     */
    double vcpuOnThisHost();

    Optional<ApplicationId> hostExclusiveTo();
}
