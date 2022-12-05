package com.yahoo.vespa.hosted.node.admin.wireguard;

import com.yahoo.config.provision.WireguardKey;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Slime;
import com.yahoo.slime.SlimeUtils;

/**
 * Wireguard parameters for a configserver.
 *
 * @author gjoranv
 */
public record ConfigserverParameters(String hostname, String endpoint, WireguardKey publicKey) {

    public static ConfigserverParameters fromJson(String json) {
        Slime slime = SlimeUtils.jsonToSlime(json);
        Cursor root = slime.get();
        return new ConfigserverParameters(
                root.field("hostname").asString(),
                root.field("endpoint").asString(),
                WireguardKey.from(root.field("publicKey").asString())
        );
    }

    public String toJson() {
        Slime slime = new Slime();
        Cursor cursor = slime.setObject();
        cursor.setString("hostname", hostname);
        cursor.setString("endpoint", endpoint);
        cursor.setString("publicKey", publicKey.value());
        return SlimeUtils.toJson(slime);
    }

}


