// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.maintenance.acl;

import com.yahoo.vespa.hosted.node.admin.task.util.file.Editor;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author freva
 */
public class NatTableLineEditorTest {

    @Test
    void nat_set_redirect_rule_without_touching_docker_rules() {
        assertNatTableLineEditorResult(
                "-A OUTPUT -d 3001::1/128 -j REDIRECT",

                "-P PREROUTING ACCEPT\n" +
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
                        "-A DOCKER_POSTROUTING -s 127.0.0.11/32 -p udp -m udp --sport 57392 -j SNAT --to-source :53\n",

                "-P PREROUTING ACCEPT\n" +
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
                        "-A OUTPUT -d 3001::1/128 -j REDIRECT");
    }

    @Test
    void nat_cleanup_wrong_redirect_rules() {
        assertNatTableLineEditorResult(
                "-A OUTPUT -d 3001::1/128 -j REDIRECT",

                "-P PREROUTING ACCEPT\n" +
                        "-P INPUT ACCEPT\n" +
                        "-P OUTPUT ACCEPT\n" +
                        "-P POSTROUTING ACCEPT\n" +
                        "-A OUTPUT -d 3001::2/128 -j REDIRECT\n",

                "-P PREROUTING ACCEPT\n" +
                        "-P INPUT ACCEPT\n" +
                        "-P OUTPUT ACCEPT\n" +
                        "-P POSTROUTING ACCEPT\n" +
                        "-A OUTPUT -d 3001::1/128 -j REDIRECT");
    }

    @Test
    void nat_delete_duplicate_rules() {
        assertNatTableLineEditorResult(
                "-A OUTPUT -d 3001::1/128 -j REDIRECT",

                "-P PREROUTING ACCEPT\n" +
                        "-P INPUT ACCEPT\n" +
                        "-P OUTPUT ACCEPT\n" +
                        "-P POSTROUTING ACCEPT\n" +
                        "-A OUTPUT -d 3001::2/128 -j REDIRECT\n" +
                        "-A OUTPUT -d 3001::1/128 -j REDIRECT\n" +
                        "-A OUTPUT -d 3001::4/128 -j REDIRECT\n",

                "-P PREROUTING ACCEPT\n" +
                        "-P INPUT ACCEPT\n" +
                        "-P OUTPUT ACCEPT\n" +
                        "-P POSTROUTING ACCEPT\n" +
                        "-A OUTPUT -d 3001::1/128 -j REDIRECT");
    }

    private static void assertNatTableLineEditorResult(String redirectRule, String currentNatTable, String expectedNatTable) {
        NatTableLineEditor natLineEditor = NatTableLineEditor.from(redirectRule);
        Editor editor = new Editor(
                "nat-table",
                () -> List.of(currentNatTable.split("\n")),
                result -> assertEquals(expectedNatTable, String.join("\n", result)),
                natLineEditor);
        editor.edit(m -> {});
    }
}