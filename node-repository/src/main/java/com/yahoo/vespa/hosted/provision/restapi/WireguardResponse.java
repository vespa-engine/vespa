// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.restapi;

import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.WireguardKeyWithTimestamp;
import com.yahoo.restapi.SlimeJsonResponse;
import com.yahoo.slime.Cursor;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.node.IP;

import java.net.InetAddress;
import java.util.List;

import static com.yahoo.vespa.hosted.provision.restapi.NodesResponse.toSlime;

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

            addConfigserver(cfgArray.addObject(), cfg.hostname(), cfg.wireguardPubKey().get(), ipAddresses);
        }
    }

    private void addConfigserver(Cursor cfgEntry, String hostname, WireguardKeyWithTimestamp keyWithTimestamp,
                                 List<String> ipAddresses) {
        cfgEntry.setString("hostname", hostname);
        NodesResponse.ipAddressesToSlime(ipAddresses, cfgEntry.setArray("ipAddresses"));
        toSlime(keyWithTimestamp, cfgEntry.setObject("wireguard"));
    }

    private static boolean isPublicIp(String ipAddress) {
        InetAddress address = IP.parse(ipAddress);
        return !address.isLoopbackAddress() && !address.isLinkLocalAddress() && !address.isSiteLocalAddress();
    }
}
