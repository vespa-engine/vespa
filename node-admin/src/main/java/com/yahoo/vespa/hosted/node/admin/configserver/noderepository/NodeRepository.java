// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.configserver.noderepository;

import com.yahoo.vespa.hosted.node.admin.ContainerAclSpec;
import com.yahoo.vespa.hosted.node.admin.ContainerNodeSpec;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAttributes;

import java.util.List;
import java.util.Optional;

/**
 * @author stiankri
 */
public interface NodeRepository {
    List<ContainerNodeSpec> getContainersToRun(String baseHostName);

    Optional<ContainerNodeSpec> getContainerNodeSpec(String hostName);

    List<ContainerAclSpec> getContainerAclSpecs(String hostName);

    void updateNodeAttributes(String hostName, NodeAttributes nodeAttributes);

    void markAsDirty(String hostName);

    void markNodeAvailableForNewAllocation(String hostName);
}
