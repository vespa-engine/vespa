// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.maintenance.acl;

import com.google.common.net.InetAddresses;
import com.yahoo.vespa.hosted.dockerapi.Container;
import com.yahoo.vespa.hosted.dockerapi.ContainerName;
import com.yahoo.vespa.hosted.dockerapi.DockerImage;
import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.NodeRepository;
import com.yahoo.vespa.hosted.node.admin.docker.DockerOperations;
import com.yahoo.vespa.hosted.node.admin.task.util.network.IPAddressesMock;
import org.junit.Before;
import org.junit.Test;
import org.mockito.verification.VerificationMode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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

    private final IPAddressesMock ipAddresses = new IPAddressesMock();
    private final DockerOperations dockerOperations  = mock(DockerOperations.class);
    private final NodeRepository nodeRepository = mock(NodeRepository.class);
    private final List<Container> containers = new ArrayList<>();
    private final AclMaintainer aclMaintainer =
            new AclMaintainer(dockerOperations, nodeRepository, NODE_ADMIN_HOSTNAME, ipAddresses);

    @Before
    public void before() {
        when(dockerOperations.getAllManagedContainers()).thenReturn(containers);
    }

    @Test
    public void configures_container_acl() {
        Map<String, Container> containers = null;
        Map<String, Acl> acls = null;

        when(nodeRepository.getAcl(NODE_ADMIN_HOSTNAME, containers.keySet())).thenReturn(acls);

        aclMaintainer.run();

        assertAclsApplied(acls);
    }

    @Test
    public void does_not_configure_acl_if_unchanged() {
        Map<String, Container> containers = null;
        Map<String, Acl> acls = null;

        when(nodeRepository.getAcl(NODE_ADMIN_HOSTNAME, containers.keySet())).thenReturn(acls);

        aclMaintainer.run();
        aclMaintainer.run();
        aclMaintainer.run();

        assertAclsApplied(acls, times(1));
    }

    @Test
    public void does_not_configure_acl_for_stopped_container() {
        Map<String, Container> containers = null;
        Map<String, Acl> acls = null;

        when(nodeRepository.getAcl(NODE_ADMIN_HOSTNAME, containers.keySet())).thenReturn(acls);


        aclMaintainer.run();

        assertAclsApplied(acls, never());
    }

    @Test
    public void rollback_is_attempted_when_applying_acl_fail() {
        Map<String, Container> containers = null;
        Map<String, Acl> acls = null;

        doThrow(new RuntimeException("iptables command failed"))
                .doNothing()
                .when(dockerOperations)
                .executeCommandInNetworkNamespace(any(), anyVararg());

        aclMaintainer.run();

        verify(dockerOperations).executeCommandInNetworkNamespace(
                eq(ContainerName.fromHostname("dsd.dsds.ds")),
                eq("ip6tables"),
                eq("-P"),
                eq("INPUT"),
                eq("ACCEPT")
        );
    }

    private void assertAclsApplied(Map<String, Acl> acls) {
        assertAclsApplied(acls, times(1));
    }

    private void assertAclsApplied(Map<String, Acl> acls, VerificationMode verificationMode) {

        acls.forEach((containerName, acl) -> {
            String iptables = "Somehing";
            StringBuilder expectedCommand = new StringBuilder()
                    .append(iptables + " -F INPUT; ")
                    .append(iptables + " -P INPUT DROP; ")
                    .append(iptables + " -P FORWARD DROP; ")
                    .append(iptables + " -P OUTPUT ACCEPT; ")
                    .append(iptables + " -A INPUT -m state --state RELATED,ESTABLISHED -j ACCEPT; ")
                    .append(iptables + " -A INPUT -i lo -j ACCEPT; ")
                    .append(iptables + " -A INPUT -p ipv6-icmp -j ACCEPT; ");

            acl.trustedNodes().forEach(node ->
                    expectedCommand.append(iptables + " -A INPUT -s " +
                            InetAddresses.toAddrString(node) +
                            "TOD" + " -j ACCEPT; "));

            acl.trustedPorts().forEach(node ->
                    expectedCommand.append(iptables + " -A INPUT -p tcp --dport " + node + " -j ACCEPT; "));

            verify(dockerOperations, verificationMode).executeCommandInNetworkNamespace(
                    eq(ContainerName.fromHostname(containerName)), eq("/bin/sh"), eq("-c"), eq(expectedCommand.toString()));
        });
    }

    private Container makeContainer(String hostname, Container.State state, int pid) {
        final ContainerName containerName = new ContainerName(hostname);
        final Container container = new Container(hostname, new DockerImage("mock"), null,
                containerName, state, pid);
        containers.add(container);
        return container;
    }
}
