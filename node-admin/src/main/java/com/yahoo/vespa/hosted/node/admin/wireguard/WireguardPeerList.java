package com.yahoo.vespa.hosted.node.admin.wireguard;

import java.util.List;

/**
 * @author gjoranv
 */
public record WireguardPeerList(List<WireguardPeer> peers, Type type) {

    public static WireguardPeerList configserverPeersFrom(List<WireguardPeer> peers) {
        return new WireguardPeerList(peers, Type.CONFIGSERVER);
    }

    public static WireguardPeerList tenantPeersFrom(List<WireguardPeer> peers) {
        return new WireguardPeerList(peers, Type.TENANT);
    }

    public enum Type {
        CONFIGSERVER,
        TENANT
    }

}
