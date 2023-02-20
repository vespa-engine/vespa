package com.yahoo.vespa.hosted.node.admin.wireguard;

import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.WireguardKey;
import com.yahoo.vespa.hosted.node.admin.task.util.network.VersionedIpAddress;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author gjoranv
 */
public class WireguardPeerTest {

    @Test
    void peers_are_sorted_by_hostname_ascending() {
        List<WireguardPeer> peers = Stream.of(
                peer("b"),
                peer("a"),
                peer("c")
        ).sorted().toList();

        assertEquals("a", peers.get(0).hostname().value());
        assertEquals("b", peers.get(1).hostname().value());
        assertEquals("c", peers.get(2).hostname().value());
    }

    private static WireguardPeer peer(String hostname) {
        return new WireguardPeer(HostName.of(hostname), List.of(VersionedIpAddress.from("::1:1")),
                                 WireguardKey.generateRandomForTesting());
    }
}
