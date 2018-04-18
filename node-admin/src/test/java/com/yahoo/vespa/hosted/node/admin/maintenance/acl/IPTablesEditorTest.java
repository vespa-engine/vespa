package com.yahoo.vespa.hosted.node.admin.maintenance.acl;

import com.google.common.net.InetAddresses;
import com.yahoo.vespa.hosted.dockerapi.ContainerName;
import com.yahoo.vespa.hosted.dockerapi.ProcessResult;
import com.yahoo.vespa.hosted.node.admin.docker.DockerOperations;
import com.yahoo.vespa.hosted.node.admin.task.util.network.IPVersion;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;

import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class IPTablesEditorTest {

    private final DockerOperations dockerOperations = mock(DockerOperations.class);
    private final ContainerName containerName = ContainerName.fromHostname("myhostname");
    private String actualRestoreContent = null;
    private final IPTablesEditor editor = new IPTablesEditor(dockerOperations, containerName, (fileContent) -> actualRestoreContent = fileContent);

    @Test
    public void filter_set_wanted_rules() {

        Acl acl = new Acl(Collections.singletonList(22), Collections.singletonList(InetAddresses.forString("3001::1")));
        FilterTableLineEditor filterLineEditor = FilterTableLineEditor.from(acl, IPVersion.IPv6);

        String currentFilterTable = "-P INPUT ACCEPT\n" +
                "-P FORWARD ACCEPT\n" +
                "-P OUTPUT ACCEPT\n";

        String expectedRestoreFileContent = "*filter\n" +
                "-P INPUT ACCEPT\n" +
                "-P FORWARD ACCEPT\n" +
                "-P OUTPUT ACCEPT\n" +
                "-A INPUT -m state --state RELATED,ESTABLISHED -j ACCEPT\n" +
                "-A INPUT -i lo -j ACCEPT\n" +
                "-A INPUT -p ipv6-icmp -j ACCEPT\n" +
                "-A INPUT -p tcp -m multiport --dports 22 -j ACCEPT\n" +
                "-A INPUT -s 3001::1/128 -j ACCEPT\n" +
                "-A INPUT -j REJECT --reject-with icmp6-port-unreachable\n" +
                "COMMIT\n";

        whenListRules(containerName, "filter", IPVersion.IPv6, currentFilterTable);

        editor.edit("filter", IPVersion.IPv6, filterLineEditor, false);

        Assert.assertEquals(expectedRestoreFileContent, actualRestoreContent);
    }

    @Test
    public void nat_set_redirect_rule_without_touching_docker_rules() {
        NatTableLineEditor natLineEditor = NatTableLineEditor.from("-A OUTPUT -d 3001::1/128 -j REDIRECT");

        String currentNatTable = "-P PREROUTING ACCEPT\n" +
                "-P INPUT ACCEPT\n" +
                "-P OUTPUT ACCEPT\n" +
                "-P POSTROUTING ACCEPT\n" +
                "-N DOCKER_OUTPUT\n" +
                "-N DOCKER_POSTROUTING\n" +
                "-A OUTPUT -d 127.0.0.11/32 -j DOCKER_OUTPUT\n" +
                "-A POSTROUTING -d 127.0.0.11/32 -j DOCKER_POSTROUTING\n" +
                "-A DOCKER_OUTPUT -d 127.0.0.11/32 -p tcp -m tcp --dport 53 -j DNAT --to-destination 127.0.0.11:43500\n" +
                "-A DOCKER_OUTPUT -d 127.0.0.11/32 -p udp -m udp --dport 53 -j DNAT --to-destination 127.0.0.11:57392\n" +
                "-A DOCKER_POSTROUTING -s 127.0.0.11/32 -p tcp -m tcp --sport 43500 -j SNAT --to-source :53\n" +
                "-A DOCKER_POSTROUTING -s 127.0.0.11/32 -p udp -m udp --sport 57392 -j SNAT --to-source :53\n";

        String expectedRestoreFileContent = "*nat\n-P PREROUTING ACCEPT\n" +
                "-P INPUT ACCEPT\n" +
                "-P OUTPUT ACCEPT\n" +
                "-P POSTROUTING ACCEPT\n" +
                "-N DOCKER_OUTPUT\n" +
                "-N DOCKER_POSTROUTING\n" +
                "-A OUTPUT -d 127.0.0.11/32 -j DOCKER_OUTPUT\n" +
                "-A POSTROUTING -d 127.0.0.11/32 -j DOCKER_POSTROUTING\n" +
                "-A DOCKER_OUTPUT -d 127.0.0.11/32 -p tcp -m tcp --dport 53 -j DNAT --to-destination 127.0.0.11:43500\n" +
                "-A DOCKER_OUTPUT -d 127.0.0.11/32 -p udp -m udp --dport 53 -j DNAT --to-destination 127.0.0.11:57392\n" +
                "-A DOCKER_POSTROUTING -s 127.0.0.11/32 -p tcp -m tcp --sport 43500 -j SNAT --to-source :53\n" +
                "-A DOCKER_POSTROUTING -s 127.0.0.11/32 -p udp -m udp --sport 57392 -j SNAT --to-source :53\n" +
                "-A OUTPUT -d 3001::1/128 -j REDIRECT\nCOMMIT\n";

        whenListRules(containerName, "nat", IPVersion.IPv6, currentNatTable);

        editor.edit("nat", IPVersion.IPv6, natLineEditor, false);

        Assert.assertEquals(expectedRestoreFileContent, actualRestoreContent);
    }

    @Test
    public void nat_cleanup_wrong_redirect_rules() {
        NatTableLineEditor natLineEditor = NatTableLineEditor.from("-A OUTPUT -d 3001::1/128 -j REDIRECT");

        String currentNatTable = "-P PREROUTING ACCEPT\n" +
                "-P INPUT ACCEPT\n" +
                "-P OUTPUT ACCEPT\n" +
                "-P POSTROUTING ACCEPT\n" +
                "-A OUTPUT -d 3001::2/128 -j REDIRECT\n";

        String expectedRestoreFileContent = "-P PREROUTING ACCEPT\n" +
                "-P INPUT ACCEPT\n" +
                "-P OUTPUT ACCEPT\n" +
                "-P POSTROUTING ACCEPT\n" +
                "-A OUTPUT -d 3001::1/128 -j REDIRECT\n";

        whenListRules(containerName, "nat", IPVersion.IPv6, currentNatTable);

        editor.edit("nat", IPVersion.IPv6, natLineEditor, false);

        Assert.assertEquals(expectedRestoreFileContent, expectedRestoreFileContent);
    }

    @Test
    public void nat_delete_duplicate_rules() {
        NatTableLineEditor natLineEditor = NatTableLineEditor.from("-A OUTPUT -d 3001::1/128 -j REDIRECT");

        String currentNatTable = "-P PREROUTING ACCEPT\n" +
                "-P INPUT ACCEPT\n" +
                "-P OUTPUT ACCEPT\n" +
                "-P POSTROUTING ACCEPT\n" +
                "-A OUTPUT -d 3001::2/128 -j REDIRECT\n" +
                "-A OUTPUT -d 3001::1/128 -j REDIRECT\n" +
                "-A OUTPUT -d 3001::4/128 -j REDIRECT\n";

        String expectedRestoreFileContent = "-P PREROUTING ACCEPT\n" +
                "-P INPUT ACCEPT\n" +
                "-P OUTPUT ACCEPT\n" +
                "-P POSTROUTING ACCEPT\n" +
                "-A OUTPUT -d 3001::1/128 -j REDIRECT\n";

        whenListRules(containerName, "nat", IPVersion.IPv6, currentNatTable);

        editor.edit("nat", IPVersion.IPv6, natLineEditor, false);

        Assert.assertEquals(expectedRestoreFileContent, expectedRestoreFileContent);
    }

    private void whenListRules(ContainerName name, String table, IPVersion ipVersion, String result) {
        when(dockerOperations.executeCommandInNetworkNamespace(
                eq(name),
                eq(ipVersion.iptablesCmd()), eq("-S"), eq("-t"), eq(table)))
                .thenReturn(new ProcessResult(0, result, ""));
    }

}
