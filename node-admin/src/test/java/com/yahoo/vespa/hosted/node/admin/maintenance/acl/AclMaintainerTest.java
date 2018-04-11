// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.maintenance.acl;

import com.google.common.net.InetAddresses;
import com.yahoo.vespa.hosted.dockerapi.Container;
import com.yahoo.vespa.hosted.dockerapi.ContainerName;
import com.yahoo.vespa.hosted.dockerapi.DockerImage;
import com.yahoo.vespa.hosted.dockerapi.ProcessResult;
import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.NodeRepository;
import com.yahoo.vespa.hosted.node.admin.docker.DockerOperations;
import com.yahoo.vespa.hosted.node.admin.task.util.network.IPAddressesMock;
import org.junit.Before;
import org.junit.Test;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.anyVararg;

public class AclMaintainerTest {

    private static final String NODE_ADMIN_HOSTNAME = "node-admin.region-1.yahoo.com";

    private final IPAddressesMock ipAddresses = new IPAddressesMock();
    private final DockerOperations dockerOperations = mock(DockerOperations.class);
    private final NodeRepository nodeRepository = mock(NodeRepository.class);
    private final Map<String, Container> containers = new HashMap<>();
    private final List<Container> containerList = new ArrayList<>();
    private final AclMaintainer aclMaintainer =
            new AclMaintainer(dockerOperations, nodeRepository, NODE_ADMIN_HOSTNAME, ipAddresses);

    @Before
    public void before() {
        when(dockerOperations.getAllManagedContainers()).thenReturn(containerList);
    }

    @Test
    public void configures_container_acl_when_iptables_differs() {
        Container container = addContainer("container1", Container.State.RUNNING);
        Map<String, Acl> acls = makeAcl(container.name.asString(), "4321", "2001::1");

        when(nodeRepository.getAcl(NODE_ADMIN_HOSTNAME, containers.keySet())).thenReturn(acls);

        when(dockerOperations.executeCommandInNetworkNamespace(
                eq(container.name),
                eq("ip6tables"), eq("-S"), eq("-t"), eq("filter")))
                .thenReturn(new ProcessResult(0, "", ""));

        when(dockerOperations.executeCommandInNetworkNamespace(
                eq(container.name),
                eq("ip6tables-restore"), anyVararg())).thenReturn(new ProcessResult(0, "", ""));

        aclMaintainer.run();

        verify(dockerOperations, times(2)).executeCommandInNetworkNamespace(any(), anyVararg());
    }

    @Test
    public void does_not_configure_acl_if_iptables_v6_are_ok() {
        Container container = addContainer("container1", Container.State.RUNNING);
        Map<String, Acl> acls = makeAcl(container.name.asString(), "4321,2345,22", "2001::1", "fd01:1234::4321");

        when(nodeRepository.getAcl(NODE_ADMIN_HOSTNAME, containers.keySet())).thenReturn(acls);

        String ONE_CONTAINER_IPV6 = "-P INPUT ACCEPT\n" +
                "-P FORWARD ACCEPT\n" +
                "-P OUTPUT ACCEPT\n" +
                "-A INPUT -m state --state RELATED,ESTABLISHED -j ACCEPT\n" +
                "-A INPUT -i lo -j ACCEPT\n" +
                "-A INPUT -p ipv6-icmp -j ACCEPT\n" +
                "-A INPUT -p tcp -m multiport --dports 4321,2345,22 -j ACCEPT\n" +
                "-A INPUT -s 2001::1/128 -j ACCEPT\n" +
                "-A INPUT -s fd01:1234::4321/128 -j ACCEPT\n" +
                "-A INPUT -j REJECT\n" +
                "-A OUTPUT -d 3001::1 -j REDIRECT";

        when(dockerOperations.executeCommandInNetworkNamespace(
                eq(container.name),
                eq("ip6tables"), eq("-S"), eq("-t"), eq("filter")))
                .thenReturn(new ProcessResult(0, ONE_CONTAINER_IPV6, ""));

        aclMaintainer.run();

        verify(dockerOperations, times(1)).executeCommandInNetworkNamespace(any(), anyVararg());
    }

    @Test
    public void does_not_configure_acl_if_iptables_dualstack_are_ok() {
        Container container = addContainer("container1", Container.State.RUNNING);
        Map<String, Acl> acls = makeAcl(container.name.asString(), "22,4443,2222", "2001::1");

        when(nodeRepository.getAcl(NODE_ADMIN_HOSTNAME, containers.keySet())).thenReturn(acls);

        String ONE_CONTAINER_IPV6 = "-P INPUT ACCEPT\n" +
                "-P FORWARD ACCEPT\n" +
                "-P OUTPUT ACCEPT\n" +
                "-A INPUT -m state --state RELATED,ESTABLISHED -j ACCEPT\n" +
                "-A INPUT -i lo -j ACCEPT\n" +
                "-A INPUT -p ipv6-icmp -j ACCEPT\n" +
                "-A INPUT -p tcp -m multiport --dports 22,4443,2222 -j ACCEPT\n" +
                "-A INPUT -s 2001::1/128 -j ACCEPT\n" +
                "-A INPUT -j REJECT\n" +
                "-A OUTPUT -d 3001::1 -j REDIRECT";

        when(dockerOperations.executeCommandInNetworkNamespace(
                eq(container.name),
                eq("ip6tables"), eq("-S"), eq("-t"), eq("filter")))
                .thenReturn(new ProcessResult(0, ONE_CONTAINER_IPV6, ""));

        aclMaintainer.run();

        verify(dockerOperations, times(1)).executeCommandInNetworkNamespace(any(), anyVararg());
    }


    @Test
    public void rollback_is_attempted_when_applying_acl_fail() {
        Container container = addContainer("container1", Container.State.RUNNING);
        Map<String, Acl> acls = makeAcl(container.name.asString(), "4321", "2001::1");
        when(nodeRepository.getAcl(NODE_ADMIN_HOSTNAME, containers.keySet())).thenReturn(acls);

        when(dockerOperations.executeCommandInNetworkNamespace(
                eq(container.name),
                eq("ip6tables"), eq("-S"), eq("-t"), eq("filter")))
                .thenReturn(new ProcessResult(0, "", ""));

        when(dockerOperations.executeCommandInNetworkNamespace(
                eq(container.name),
                eq("ip6tables-restore"), anyVararg())).thenThrow(new RuntimeException("iptables restore failed"));

        aclMaintainer.run();

        verify(dockerOperations, times(1)).executeCommandInNetworkNamespace(eq(container.name), eq("ip6tables"), eq("-F"));
    }

    private Container addContainer(String name, Container.State state) {
        final ContainerName containerName = new ContainerName(name);
        final Container container = new Container(name, new DockerImage("mock"), null,
                containerName, state, 2);
        containers.put(name, container);
        containerList.add(container);
        ipAddresses.addAddress(name, "3001::" + containers.size());
        return container;
    }

    private Map<String, Acl> makeAcl(String containerName, String portsCommaSeparated, String... addresses) {
        Map<String, Acl> map = new HashMap<>();

        List<Integer> ports = Arrays.stream(portsCommaSeparated.split(","))
                .map(Integer::valueOf)
                .collect(Collectors.toList());

        List<InetAddress> hosts = Arrays.stream(addresses)
                .map(InetAddresses::forString)
                .collect(Collectors.toList());

        Acl acl = new Acl(ports, hosts);
        map.put(containerName, acl);

        return map;
    }
}
