package com.yahoo.vespa.hosted.provision.restapi;

import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.WireguardKey;
import com.yahoo.restapi.SlimeJsonResponse;
import com.yahoo.slime.Cursor;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.NodeRepository;

import java.util.Set;

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

        configservers.stream()
                .filter(node -> node.wireguardPubKey().isPresent())
                .forEach(configserver -> addConfigserver(cfgArray.addObject(),
                                                         configserver.hostname(),
                                                         configserver.wireguardPubKey().get(),
                                                         configserver.ipConfig().primary()));
    }

    private void addConfigserver(Cursor cfgEntry, String hostname, WireguardKey key, Set<String> ipAddresses) {
        cfgEntry.setString("hostname", hostname);
        cfgEntry.setString("wireguardPubkey", key.value());
        NodesResponse.ipAddressesToSlime(ipAddresses, cfgEntry.setArray("ipAddresses"));
    }

}
