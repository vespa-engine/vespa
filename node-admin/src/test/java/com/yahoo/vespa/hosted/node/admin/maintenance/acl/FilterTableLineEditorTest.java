// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.maintenance.acl;

import com.yahoo.config.provision.NodeType;
import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.Acl;
import com.yahoo.vespa.hosted.node.admin.task.util.file.Editor;
import com.yahoo.vespa.hosted.node.admin.task.util.network.IPVersion;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author freva
 */
public class FilterTableLineEditorTest {

    @Test
    void filter_set_wanted_rules() {
        Acl acl = new Acl.Builder().withTrustedPorts(22).withTrustedNode("hostname", "3001::1").build();

        assertFilterTableLineEditorResult(
                acl, IPVersion.IPv6,

                "-P INPUT ACCEPT\n" +
                        "-P FORWARD ACCEPT\n" +
                        "-P OUTPUT ACCEPT\n",

                "-P INPUT ACCEPT\n" +
                        "-P FORWARD ACCEPT\n" +
                        "-P OUTPUT ACCEPT\n" +
                        "-A INPUT -m state --state RELATED,ESTABLISHED -j ACCEPT\n" +
                        "-A INPUT -i lo -j ACCEPT\n" +
                        "-A INPUT -p ipv6-icmp -j ACCEPT\n" +
                        "-A INPUT -p tcp -m multiport --dports 22 -j ACCEPT\n" +
                        "-A INPUT -s 3001::1/128 -j ACCEPT\n" +
                        "-A INPUT -j REJECT --reject-with icmp6-port-unreachable");
    }

    @Test
    void produces_minimal_diff_simple() {
        assertFilterTableDiff(List.of(2, 5, 3, 6, 1, 4), List.of(2, 5, 6, 1, 4),
                "Patching file table:\n" +
                        "--A INPUT -s 2001::3/128 -j ACCEPT\n");
    }

    @Test
    void produces_minimal_diff_complex() {
        assertFilterTableDiff(List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10), List.of(5, 11, 6, 3, 10, 4, 8, 12),
                "Patching file table:\n" +
                        "--A INPUT -s 2001::1/128 -j ACCEPT\n" +
                        "--A INPUT -s 2001::2/128 -j ACCEPT\n" +
                        "+-A INPUT -s 2001::11/128 -j ACCEPT\n" +
                        "+-A INPUT -s 2001::12/128 -j ACCEPT\n" +
                        "--A INPUT -s 2001::7/128 -j ACCEPT\n" +
                        "--A INPUT -s 2001::9/128 -j ACCEPT\n");
    }

    private static void assertFilterTableLineEditorResult(
            Acl acl, IPVersion ipVersion, String currentFilterTable, String expectedRestoreFileContent) {
        FilterTableLineEditor filterLineEditor = FilterTableLineEditor.from(acl, ipVersion);
        Editor editor = new Editor(
                "nat-table",
                () -> List.of(currentFilterTable.split("\n")),
                result -> assertEquals(expectedRestoreFileContent, String.join("\n", result)),
                filterLineEditor);
        editor.edit(m -> {});
    }

    private static void assertFilterTableDiff(List<Integer> currentIpSuffix, List<Integer> wantedIpSuffix, String diff) {
        Acl.Builder currentAcl = new Acl.Builder();
        NodeType nodeType = NodeType.tenant;
        currentIpSuffix.forEach(i -> currentAcl.withTrustedNode("host" + i, "2001::" + i));
        List<String> currentTable = new ArrayList<>();

        Acl.Builder wantedAcl = new Acl.Builder();
        wantedIpSuffix.forEach(i -> wantedAcl.withTrustedNode("host" + i, "2001::" + i));

        new Editor("table", List::of, currentTable::addAll, FilterTableLineEditor.from(currentAcl.build(), IPVersion.IPv6))
                .edit(log -> {});

        new Editor("table", () -> currentTable, result -> {}, FilterTableLineEditor.from(wantedAcl.build(), IPVersion.IPv6))
                .edit(log -> assertEquals(diff, log));
    }

}
