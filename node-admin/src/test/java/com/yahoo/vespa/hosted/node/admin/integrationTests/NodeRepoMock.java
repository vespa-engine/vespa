// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.integrationTests;

import com.yahoo.config.provision.NodeType;
import com.yahoo.vespa.hosted.node.admin.ContainerAclSpec;
import com.yahoo.vespa.hosted.node.admin.ContainerNodeSpec;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAttributes;
import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.NodeRepository;
import com.yahoo.vespa.hosted.provision.Node;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Mock with some simple logic
 *
 * @author dybis
 */
public class NodeRepoMock implements NodeRepository {
    private static final Object monitor = new Object();

    private final Map<String, ContainerNodeSpec> containerNodeSpecsByHostname = new HashMap<>();
    private final Map<String, List<ContainerAclSpec>> acls = new HashMap<>();
    private final CallOrderVerifier callOrderVerifier;

    public NodeRepoMock(CallOrderVerifier callOrderVerifier) {
        this.callOrderVerifier = callOrderVerifier;
    }

    @Override
    public List<ContainerNodeSpec> getContainersToRun(String dockerHostHostname) {
        synchronized (monitor) {
            return new ArrayList<>(containerNodeSpecsByHostname.values());
        }
    }

    @Override
    public Optional<ContainerNodeSpec> getContainerNodeSpec(String hostName) {
        synchronized (monitor) {
            return Optional.ofNullable(containerNodeSpecsByHostname.get(hostName));
        }
    }

    @Override
    public List<ContainerAclSpec> getContainerAclSpecs(String hostName) {
        synchronized (monitor) {
            return Optional.ofNullable(acls.get(hostName))
                    .orElseGet(Collections::emptyList);
        }
    }

    @Override
    public void updateNodeAttributes(String hostName, NodeAttributes nodeAttributes) {
        synchronized (monitor) {
            callOrderVerifier.add("updateNodeAttributes with HostName: " + hostName + ", " + nodeAttributes);
        }
    }

    @Override
    public void markAsDirty(String hostName) {
        Optional<ContainerNodeSpec> cns = getContainerNodeSpec(hostName);

        synchronized (monitor) {
            cns.ifPresent(containerNodeSpec -> updateContainerNodeSpec(new ContainerNodeSpec.Builder(containerNodeSpec)
                    .nodeState(Node.State.dirty)
                    .nodeType(NodeType.tenant)
                    .nodeFlavor("docker")
                    .build()));
            callOrderVerifier.add("markAsDirty with HostName: " + hostName);
        }
    }

    @Override
    public void markNodeAvailableForNewAllocation(String hostName) {
        Optional<ContainerNodeSpec> cns = getContainerNodeSpec(hostName);

        synchronized (monitor) {
            if (cns.isPresent()) {
                updateContainerNodeSpec(new ContainerNodeSpec.Builder(cns.get())
                        .nodeState(Node.State.ready)
                        .nodeType(NodeType.tenant)
                        .nodeFlavor("docker")
                        .build());
            }
            callOrderVerifier.add("markNodeAvailableForNewAllocation with HostName: " + hostName);
        }
    }

    public void updateContainerNodeSpec(ContainerNodeSpec containerNodeSpec) {
        containerNodeSpecsByHostname.put(containerNodeSpec.hostname, containerNodeSpec);
    }

    public int getNumberOfContainerSpecs() {
        synchronized (monitor) {
            return containerNodeSpecsByHostname.size();
        }
    }
}
