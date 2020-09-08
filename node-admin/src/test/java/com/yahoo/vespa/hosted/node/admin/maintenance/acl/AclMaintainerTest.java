// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.maintenance.acl;

import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.Acl;
import com.yahoo.vespa.hosted.node.admin.docker.ContainerOperations;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgentContext;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgentContextImpl;
import com.yahoo.vespa.hosted.node.admin.task.util.file.UnixPath;
import com.yahoo.vespa.hosted.node.admin.task.util.network.IPAddressesMock;
import com.yahoo.vespa.hosted.node.admin.task.util.network.IPVersion;
import com.yahoo.vespa.hosted.node.admin.task.util.process.CommandResult;
import com.yahoo.vespa.test.file.TestFileSystem;
import org.junit.Before;
import org.junit.Test;

import java.nio.file.FileSystem;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.endsWith;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

public class AclMaintainerTest {

    private static final String EMPTY_FILTER_TABLE = "-P INPUT ACCEPT\n-P FORWARD ACCEPT\n-P OUTPUT ACCEPT\n";
    private static final String EMPTY_NAT_TABLE = "-P PREROUTING ACCEPT\n-P INPUT ACCEPT\n-P OUTPUT ACCEPT\n-P POSTROUTING ACCEPT\n";

    private final ContainerOperations containerOperations = mock(ContainerOperations.class);
    private final IPAddressesMock ipAddresses = new IPAddressesMock();
    private final AclMaintainer aclMaintainer = new AclMaintainer(containerOperations, ipAddresses);

    private final FileSystem fileSystem = TestFileSystem.create();
    private final Function<Acl, NodeAgentContext> contextGenerator =
            acl -> new NodeAgentContextImpl.Builder("container1.host.com").fileSystem(fileSystem).acl(acl).build();
    private final List<String> writtenFileContents = new ArrayList<>();

    @Test
    public void configures_full_container_acl_from_empty() {
        Acl acl = new Acl.Builder().withTrustedPorts(22, 4443)
                .withTrustedNode("hostname1", "3001::abcd")
                .withTrustedNode("hostname2", "3001::1234")
                .withTrustedNode("hostname1", "192.168.0.5")
                .withTrustedNode("hostname4", "172.16.5.234").build();
        NodeAgentContext context = contextGenerator.apply(acl);

        ipAddresses.addAddress(context.hostname().value(), "2001::1");
        ipAddresses.addAddress(context.hostname().value(), "10.0.0.1");

        whenListRules(context, "filter", IPVersion.IPv4, EMPTY_FILTER_TABLE);
        whenListRules(context, "filter", IPVersion.IPv6, EMPTY_FILTER_TABLE);
        whenListRules(context, "nat", IPVersion.IPv4, EMPTY_NAT_TABLE);
        whenListRules(context, "nat", IPVersion.IPv6, EMPTY_NAT_TABLE);

        aclMaintainer.converge(context);

        verify(containerOperations, times(4)).executeCommandInNetworkNamespace(eq(context), any(), eq("-S"), eq("-t"), any());
        verify(containerOperations, times(2)).executeCommandInNetworkNamespace(eq(context), eq("iptables-restore"), any());
        verify(containerOperations, times(2)).executeCommandInNetworkNamespace(eq(context), eq("ip6tables-restore"), any());
        verifyNoMoreInteractions(containerOperations);

        List<String> expected = List.of(
                // IPv4 filter table restore
                "*filter\n" +
                "-P INPUT ACCEPT\n" +
                "-P FORWARD ACCEPT\n" +
                "-P OUTPUT ACCEPT\n" +
                "-A INPUT -m state --state RELATED,ESTABLISHED -j ACCEPT\n" +
                "-A INPUT -i lo -j ACCEPT\n" +
                "-A INPUT -p icmp -j ACCEPT\n" +
                "-A INPUT -p tcp -m multiport --dports 22,4443 -j ACCEPT\n" +
                "-A INPUT -s 172.16.5.234/32 -j ACCEPT\n" +
                "-A INPUT -s 192.168.0.5/32 -j ACCEPT\n" +
                "-A INPUT -j REJECT --reject-with icmp-port-unreachable\n" +
                "COMMIT\n",

                // IPv6 filter table restore
                "*filter\n" +
                "-P INPUT ACCEPT\n" +
                "-P FORWARD ACCEPT\n" +
                "-P OUTPUT ACCEPT\n" +
                "-A INPUT -m state --state RELATED,ESTABLISHED -j ACCEPT\n" +
                "-A INPUT -i lo -j ACCEPT\n" +
                "-A INPUT -p ipv6-icmp -j ACCEPT\n" +
                "-A INPUT -p tcp -m multiport --dports 22,4443 -j ACCEPT\n" +
                "-A INPUT -s 3001::1234/128 -j ACCEPT\n" +
                "-A INPUT -s 3001::abcd/128 -j ACCEPT\n" +
                "-A INPUT -j REJECT --reject-with icmp6-port-unreachable\n" +
                "COMMIT\n",

                // IPv4 nat table restore
                "*nat\n" +
                "-P PREROUTING ACCEPT\n" +
                "-P INPUT ACCEPT\n" +
                "-P OUTPUT ACCEPT\n" +
                "-P POSTROUTING ACCEPT\n" +
                "-A OUTPUT -d 10.0.0.1/32 -j REDIRECT\n" +
                "COMMIT\n",

                // IPv6 nat table restore
                "*nat\n" +
                "-P PREROUTING ACCEPT\n" +
                "-P INPUT ACCEPT\n" +
                "-P OUTPUT ACCEPT\n" +
                "-P POSTROUTING ACCEPT\n" +
                "-A OUTPUT -d 2001::1/128 -j REDIRECT\n" +
                "COMMIT\n");
        assertEquals(expected, writtenFileContents);
    }

    @Test
    public void configures_minimal_container_acl_from_empty() {
        // The ACL spec is empty and our this node's addresses do not resolve
        Acl acl = new Acl.Builder().withTrustedPorts().build();
        NodeAgentContext context = contextGenerator.apply(acl);

        whenListRules(context, "filter", IPVersion.IPv4, EMPTY_FILTER_TABLE);
        whenListRules(context, "filter", IPVersion.IPv6, EMPTY_FILTER_TABLE);
        whenListRules(context, "nat", IPVersion.IPv4, EMPTY_NAT_TABLE);
        whenListRules(context, "nat", IPVersion.IPv6, EMPTY_NAT_TABLE);

        aclMaintainer.converge(context);

        verify(containerOperations, times(2)).executeCommandInNetworkNamespace(eq(context), any(), eq("-S"), eq("-t"), any());
        verify(containerOperations, times(1)).executeCommandInNetworkNamespace(eq(context), eq("iptables-restore"), any());
        verify(containerOperations, times(1)).executeCommandInNetworkNamespace(eq(context), eq("ip6tables-restore"), any());
        verifyNoMoreInteractions(containerOperations);

        List<String> expected = List.of(
                // IPv4 filter table restore
                "*filter\n" +
                "-P INPUT ACCEPT\n" +
                "-P FORWARD ACCEPT\n" +
                "-P OUTPUT ACCEPT\n" +
                "-A INPUT -m state --state RELATED,ESTABLISHED -j ACCEPT\n" +
                "-A INPUT -i lo -j ACCEPT\n" +
                "-A INPUT -p icmp -j ACCEPT\n" +
                "-A INPUT -j REJECT --reject-with icmp-port-unreachable\n" +
                "COMMIT\n",

                // IPv6 filter table restore
                "*filter\n" +
                "-P INPUT ACCEPT\n" +
                "-P FORWARD ACCEPT\n" +
                "-P OUTPUT ACCEPT\n" +
                "-A INPUT -m state --state RELATED,ESTABLISHED -j ACCEPT\n" +
                "-A INPUT -i lo -j ACCEPT\n" +
                "-A INPUT -p ipv6-icmp -j ACCEPT\n" +
                "-A INPUT -j REJECT --reject-with icmp6-port-unreachable\n" +
                "COMMIT\n");
        assertEquals(expected, writtenFileContents);
    }

    @Test
    public void only_configure_iptables_for_ipversion_that_differs() {
        Acl acl = new Acl.Builder().withTrustedPorts(22, 4443).withTrustedNode("hostname1", "3001::abcd").build();
        NodeAgentContext context = contextGenerator.apply(acl);

        ipAddresses.addAddress(context.hostname().value(), "2001::1");

        whenListRules(context, "filter", IPVersion.IPv4, EMPTY_FILTER_TABLE);
        whenListRules(context, "filter", IPVersion.IPv6,
                "-P INPUT ACCEPT\n" +
                "-P FORWARD ACCEPT\n" +
                "-P OUTPUT ACCEPT\n" +
                "-A INPUT -m state --state RELATED,ESTABLISHED -j ACCEPT\n" +
                "-A INPUT -i lo -j ACCEPT\n" +
                "-A INPUT -p ipv6-icmp -j ACCEPT\n" +
                "-A INPUT -p tcp -m multiport --dports 22,4443 -j ACCEPT\n" +
                "-A INPUT -s 3001::abcd/128 -j ACCEPT\n" +
                "-A INPUT -j REJECT --reject-with icmp6-port-unreachable\n");
        whenListRules(context, "nat", IPVersion.IPv6,
                "-P PREROUTING ACCEPT\n" +
                "-P INPUT ACCEPT\n" +
                "-P OUTPUT ACCEPT\n" +
                "-P POSTROUTING ACCEPT\n" +
                "-A OUTPUT -d 2001::1/128 -j REDIRECT\n");

        aclMaintainer.converge(context);

        verify(containerOperations, times(3)).executeCommandInNetworkNamespace(eq(context), any(), eq("-S"), eq("-t"), any());
        verify(containerOperations, times(1)).executeCommandInNetworkNamespace(eq(context), eq("iptables-restore"), any());
        verify(containerOperations, never()).executeCommandInNetworkNamespace(eq(context), eq("ip6tables-restore"), any()); //we don't have a ip4 address for the container so no redirect
        verifyNoMoreInteractions(containerOperations);

        List<String> expected = List.of(
                "*filter\n" +
                "-P INPUT ACCEPT\n" +
                "-P FORWARD ACCEPT\n" +
                "-P OUTPUT ACCEPT\n" +
                "-A INPUT -m state --state RELATED,ESTABLISHED -j ACCEPT\n" +
                "-A INPUT -i lo -j ACCEPT\n" +
                "-A INPUT -p icmp -j ACCEPT\n" +
                "-A INPUT -p tcp -m multiport --dports 22,4443 -j ACCEPT\n" +
                "-A INPUT -j REJECT --reject-with icmp-port-unreachable\n" +
                "COMMIT\n");
        assertEquals(expected, writtenFileContents);
    }

    @Test
    public void rollback_is_attempted_when_applying_acl_fail() {
        Acl acl = new Acl.Builder().withTrustedPorts(22, 4443).withTrustedNode("hostname1", "3001::abcd").build();
        NodeAgentContext context = contextGenerator.apply(acl);

        ipAddresses.addAddress(context.hostname().value(), "2001::1");

        whenListRules(context, "filter", IPVersion.IPv4, EMPTY_FILTER_TABLE);
        whenListRules(context, "filter", IPVersion.IPv6,
                "-P INPUT ACCEPT\n" +
                "-P FORWARD ACCEPT\n" +
                "-P OUTPUT ACCEPT\n" +
                "-A INPUT -m state --state RELATED,ESTABLISHED -j ACCEPT\n" +
                "-A INPUT -i lo -j ACCEPT\n" +
                "-A INPUT -p ipv6-icmp -j ACCEPT\n" +
                "-A INPUT -p tcp -m multiport --dports 22,4443 -j ACCEPT\n" +
                "-A INPUT -s 3001::abcd/128 -j ACCEPT\n" +
                "-A INPUT -j REJECT --reject-with icmp6-port-unreachable\n");
        whenListRules(context, "nat", IPVersion.IPv6,
                "-P PREROUTING ACCEPT\n" +
                "-P INPUT ACCEPT\n" +
                "-P OUTPUT ACCEPT\n" +
                "-P POSTROUTING ACCEPT\n" +
                "-A OUTPUT -d 2001::1/128 -j REDIRECT\n");

        when(containerOperations.executeCommandInNetworkNamespace(eq(context), eq("iptables-restore"), any()))
                .thenThrow(new RuntimeException("iptables restore failed"));

        aclMaintainer.converge(context);

        verify(containerOperations, times(3)).executeCommandInNetworkNamespace(eq(context), any(), eq("-S"), eq("-t"), any());
        verify(containerOperations, times(1)).executeCommandInNetworkNamespace(eq(context), eq("iptables-restore"), any());
        verify(containerOperations, times(1)).executeCommandInNetworkNamespace(eq(context), eq("iptables"), eq("-F"), eq("-t"), eq("filter"));
        verifyNoMoreInteractions(containerOperations);

        aclMaintainer.converge(context);
    }

    @Before
    public void setup() {
        doAnswer(invoc -> {
            String path = invoc.getArgument(2);
            writtenFileContents.add(new UnixPath(path).readUtf8File());
            return new CommandResult(null, 0, "");
        }).when(containerOperations).executeCommandInNetworkNamespace(any(), endsWith("-restore"), any());
    }

    private void whenListRules(NodeAgentContext context, String table, IPVersion ipVersion, String output) {
        when(containerOperations.executeCommandInNetworkNamespace(
                eq(context), eq(ipVersion.iptablesCmd()), eq("-S"), eq("-t"), eq(table)))
                .thenReturn(new CommandResult(null, 0, output));
    }
}
