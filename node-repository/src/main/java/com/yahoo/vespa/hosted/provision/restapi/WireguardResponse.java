package com.yahoo.vespa.hosted.provision.restapi;

import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.WireguardKey;
import com.yahoo.restapi.SlimeJsonResponse;
import com.yahoo.slime.Cursor;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.node.IP;

import java.net.InetAddress;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * A response containing the wireguard peer config for each configserver that has a public key.
 *
 * @author gjoranv
 */
public class WireguardResponse extends SlimeJsonResponse {

    public WireguardResponse(NodeRepository nodeRepository) {
        Cursor root = slime.setObject();
        Cursor cfgArray = root.setArray("configservers");

        NodeList configservers = nodeRepository.nodes()
                .list(Node.State.active)
                .nodeType(NodeType.config);

        for (Node cfg : configservers) {
            if (cfg.wireguardPubKey().isEmpty()) continue;
            List<String> ipAddresses = cfg.ipConfig().primary().stream()
                    .filter(WireguardResponse::isPublicIp)
                    .toList();
            if (ipAddresses.isEmpty()) continue;

            addConfigserver(cfgArray.addObject(), cfg.hostname(), cfg.wireguardPubKey().get(),
                            cfg.wireguardKeyTimestamp(), ipAddresses);
        }
    }

    private void addConfigserver(Cursor cfgEntry, String hostname, WireguardKey key, Optional<Instant> keyTimestamp,
                                 List<String> ipAddresses) {
        cfgEntry.setString("hostname", hostname);
        cfgEntry.setString("wireguardPubkey", key.value());
        cfgEntry.setLong("wireguardKeyTimestamp", keyTimestamp.orElse(Instant.EPOCH).toEpochMilli());
        NodesResponse.ipAddressesToSlime(ipAddresses, cfgEntry.setArray("ipAddresses"));
    }

    private static boolean isPublicIp(String ipAddress) {
        InetAddress address = IP.parse(ipAddress);
        return !address.isLoopbackAddress() && !address.isLinkLocalAddress() && !address.isSiteLocalAddress();
    }
}
