// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.maintenance.acl;

import com.yahoo.vespa.hosted.dockerapi.Container;
import com.yahoo.vespa.hosted.dockerapi.ContainerName;
import com.yahoo.vespa.hosted.dockerapi.DockerImage;
import com.yahoo.vespa.hosted.node.admin.ContainerAclSpec;
import com.yahoo.vespa.hosted.node.admin.docker.DockerOperations;
import com.yahoo.vespa.hosted.node.admin.noderepository.NodeRepository;
import org.junit.Before;
import org.junit.Test;
import org.mockito.verification.VerificationMode;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyVararg;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AclMaintainerTest {

    private static final String NODE_ADMIN_HOSTNAME = "node-admin.region-1.yahoo.com";

    private AclMaintainer aclMaintainer;
    private DockerOperations dockerOperations;
    private NodeRepository nodeRepository;
    private List<Container> containers;

    @Before
    public void before() {
        this.dockerOperations = mock(DockerOperations.class);
        this.nodeRepository = mock(NodeRepository.class);
        this.aclMaintainer = new AclMaintainer(dockerOperations, nodeRepository, NODE_ADMIN_HOSTNAME);
        this.containers = new ArrayList<>();
        when(dockerOperations.getAllManagedContainers()).thenReturn(containers);
    }

    @Test
    public void configures_container_acl() {
        Container container = makeContainer("container-1");
        List<ContainerAclSpec> aclSpecs = makeAclSpecs(3, container.name);
        when(nodeRepository.getContainerAclSpecs(NODE_ADMIN_HOSTNAME)).thenReturn(aclSpecs);
        aclMaintainer.run();
        assertAclsApplied(container.name, aclSpecs);
    }

    @Test
    public void does_not_configure_acl_if_unchanged() {
        Container container = makeContainer("container-1");
        List<ContainerAclSpec> aclSpecs = makeAclSpecs(3, container.name);
        when(nodeRepository.getContainerAclSpecs(NODE_ADMIN_HOSTNAME)).thenReturn(aclSpecs);
        // Run twice
        aclMaintainer.run();
        aclMaintainer.run();
        assertAclsApplied(container.name, aclSpecs, times(1));
    }

    @Test
    public void reconfigures_acl_when_container_pid_changes() {
        Container container = makeContainer("container-1");
        List<ContainerAclSpec> aclSpecs = makeAclSpecs(3, container.name);
        when(nodeRepository.getContainerAclSpecs(NODE_ADMIN_HOSTNAME)).thenReturn(aclSpecs);

        aclMaintainer.run();
        assertAclsApplied(container.name, aclSpecs);

        // Container is restarted and PID changes
        makeContainer(container.name.asString(), Container.State.RUNNING, 43);
        aclMaintainer.run();

        assertAclsApplied(container.name, aclSpecs, times(2));
    }

    @Test
    public void does_not_configure_acl_for_stopped_container() {
        Container stoppedContainer = makeContainer("container-1", Container.State.EXITED, 0);
        List<ContainerAclSpec> aclSpecs = makeAclSpecs(1, stoppedContainer.name);
        when(nodeRepository.getContainerAclSpecs(NODE_ADMIN_HOSTNAME)).thenReturn(aclSpecs);
        aclMaintainer.run();
        assertAclsApplied(stoppedContainer.name, aclSpecs, never());
    }

    @Test
    public void rollback_is_attempted_when_applying_acl_fail() {
        Container container = makeContainer("container-1");
        when(nodeRepository.getContainerAclSpecs(NODE_ADMIN_HOSTNAME)).thenReturn(makeAclSpecs(1, container.name));

        doThrow(new RuntimeException("iptables command failed"))
                .doNothing()
                .when(dockerOperations)
                .executeCommandInNetworkNamespace(any(), anyVararg());

        aclMaintainer.run();

        verify(dockerOperations).executeCommandInNetworkNamespace(
                eq(container.name),
                eq("ip6tables"),
                eq("-P"),
                eq("INPUT"),
                eq("ACCEPT")
        );
    }

    private void assertAclsApplied(ContainerName containerName, List<ContainerAclSpec> containerAclSpecs) {
        assertAclsApplied(containerName, containerAclSpecs, times(1));
    }

    private void assertAclsApplied(ContainerName containerName, List<ContainerAclSpec> containerAclSpecs,
                                   VerificationMode verificationMode) {
        verify(dockerOperations, verificationMode).executeCommandInNetworkNamespace(
                eq(containerName),
                eq("ip6tables"),
                eq("-F"),
                eq("INPUT")
        );
        verify(dockerOperations, verificationMode).executeCommandInNetworkNamespace(
                eq(containerName),
                eq("ip6tables"),
                eq("-P"),
                eq("INPUT"),
                eq("DROP")
        );
        verify(dockerOperations, verificationMode).executeCommandInNetworkNamespace(
                eq(containerName),
                eq("ip6tables"),
                eq("-P"),
                eq("FORWARD"),
                eq("DROP")
        );
        verify(dockerOperations, verificationMode).executeCommandInNetworkNamespace(
                eq(containerName),
                eq("ip6tables"),
                eq("-P"),
                eq("OUTPUT"),
                eq("ACCEPT")
        );
        verify(dockerOperations, verificationMode).executeCommandInNetworkNamespace(
                eq(containerName),
                eq("ip6tables"),
                eq("-A"),
                eq("INPUT"),
                eq("-m"),
                eq("state"),
                eq("--state"),
                eq("RELATED,ESTABLISHED"),
                eq("-j"),
                eq("ACCEPT")
        );
        verify(dockerOperations, verificationMode).executeCommandInNetworkNamespace(
                eq(containerName),
                eq("ip6tables"),
                eq("-A"),
                eq("INPUT"),
                eq("-i"),
                eq("lo"),
                eq("-j"),
                eq("ACCEPT")
        );
        verify(dockerOperations, verificationMode).executeCommandInNetworkNamespace(
                eq(containerName),
                eq("ip6tables"),
                eq("-A"),
                eq("INPUT"),
                eq("-p"),
                eq("ipv6-icmp"),
                eq("-j"),
                eq("ACCEPT")
        );
        containerAclSpecs.forEach(aclSpec -> verify(dockerOperations, verificationMode).executeCommandInNetworkNamespace(
                eq(containerName),
                eq("ip6tables"),
                eq("-A"),
                eq("INPUT"),
                eq("-s"),
                eq(aclSpec.ipAddress() + "/128"),
                eq("-j"),
                eq("ACCEPT")
        ));
        verify(dockerOperations, verificationMode).executeCommandInNetworkNamespace(
                eq(containerName),
                eq("ip6tables"),
                eq("-A"),
                eq("INPUT"),
                eq("-j"),
                eq("REJECT")
        );
    }

    private Container makeContainer(String hostname) {
        return makeContainer(hostname, Container.State.RUNNING, 42);
    }

    private Container makeContainer(String hostname, Container.State state, int pid) {
        final ContainerName containerName = new ContainerName(hostname);
        final Container container = new Container(hostname, new DockerImage("mock"),
                containerName, state, pid);
        containers.add(container);
        return container;
    }

    private static List<ContainerAclSpec> makeAclSpecs(int count, ContainerName containerName) {
        return IntStream.rangeClosed(1, count)
                .mapToObj(i -> new ContainerAclSpec("node-" + i, "::" + i, containerName))
                .collect(Collectors.toList());
    }

}
