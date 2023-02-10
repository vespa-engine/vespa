package com.yahoo.vespa.hosted.provision.restapi;

import com.yahoo.config.provision.NodeType;
import com.yahoo.restapi.SlimeJsonResponse;
import com.yahoo.slime.Cursor;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.NodeRepository;

/**
 * @author gjoranv
 */
public class WireguardResponse extends SlimeJsonResponse {

    public WireguardResponse(NodeRepository nodeRepository) {
        Cursor root = slime.setObject();
        Cursor cfgArray = root.setArray("configservers");

        NodeList configservers = nodeRepository.nodes()
                .list(Node.State.active)
                .nodeType(NodeType.config);

        configservers.forEach(
                configserver -> addConfigserver(cfgArray.addObject(), configserver));
    }

    private void addConfigserver(Cursor cfgEntry, Node configserver) {
        cfgEntry.setString("hostname", configserver.hostname());

        configserver.wireguardPubKey().ifPresent(
                key -> cfgEntry.setString("wireguardPubkey", key.value()));

        NodesResponse.ipAddressesToSlime(configserver.ipConfig().primary(), cfgEntry.setArray("ipAddresses"));
    }

}
