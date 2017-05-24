// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.integrationTests;

import com.yahoo.vespa.hosted.node.admin.ContainerAclSpec;
import com.yahoo.vespa.hosted.node.admin.ContainerNodeSpec;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAttributes;
import com.yahoo.vespa.hosted.node.admin.noderepository.NodeRepository;
import com.yahoo.vespa.hosted.provision.Node;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Mock with some simple logic
 *
 * @author dybis
 */
public class NodeRepoMock implements NodeRepository {

    private static final Object monitor = new Object();

    private List<ContainerNodeSpec> containerNodeSpecs = new ArrayList<>();
    private final Map<String, List<ContainerAclSpec>> acls = new HashMap<>();
    private final CallOrderVerifier callOrderVerifier;

    public NodeRepoMock(CallOrderVerifier callOrderVerifier) {
        this.callOrderVerifier = callOrderVerifier;
    }

    @Override
    public List<ContainerNodeSpec> getContainersToRun() throws IOException {
        synchronized (monitor) {
            return containerNodeSpecs;
        }
    }

    @Override
    public Optional<ContainerNodeSpec> getContainerNodeSpec(String hostName) {
        synchronized (monitor) {
            return containerNodeSpecs.stream()
                    .filter(containerNodeSpec -> containerNodeSpec.hostname.equals(hostName))
                    .findFirst();
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
                    .nodeType("tenant")
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
                        .nodeType("tenant")
                        .nodeFlavor("docker")
                        .build());
            }
            callOrderVerifier.add("markNodeAvailableForNewAllocation with HostName: " + hostName);
        }
    }

    public void updateContainerNodeSpec(ContainerNodeSpec containerNodeSpec) {
        addContainerNodeSpec(containerNodeSpec);
    }

    public void addContainerNodeSpec(ContainerNodeSpec containerNodeSpec) {
        removeContainerNodeSpec(containerNodeSpec.hostname);
        synchronized (monitor) {
            containerNodeSpecs.add(containerNodeSpec);
        }
    }

    public void clearContainerNodeSpecs() {
        synchronized (monitor) {
            containerNodeSpecs.clear();
        }
    }

    public void removeContainerNodeSpec(String hostName) {
        synchronized (monitor) {
            containerNodeSpecs = containerNodeSpecs.stream()
                    .filter(c -> !c.hostname.equals(hostName))
                    .collect(Collectors.toList());
        }
    }

    public int getNumberOfContainerSpecs() {
        synchronized (monitor) {
            return containerNodeSpecs.size();
        }
    }

    public void addContainerAclSpecs(String hostname, List<ContainerAclSpec> containerAclSpecs) {
        synchronized (monitor) {
            if (this.acls.containsKey(hostname)) {
                this.acls.get(hostname).addAll(containerAclSpecs);
            } else {
                this.acls.put(hostname, containerAclSpecs);
            }
        }
    }
}
