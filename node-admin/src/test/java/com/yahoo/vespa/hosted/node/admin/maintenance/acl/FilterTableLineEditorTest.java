// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.maintenance.acl;

import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.Acl;
import com.yahoo.vespa.hosted.node.admin.task.util.file.Editor;
import com.yahoo.vespa.hosted.node.admin.task.util.network.IPVersion;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * @author freva
 */
public class FilterTableLineEditorTest {

    @Test
    public void filter_set_wanted_rules() {
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
}