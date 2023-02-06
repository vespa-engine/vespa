package com.yahoo.vespa.hosted.node.admin.wireguard;

import com.yahoo.config.provision.WireguardKey;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Slime;
import com.yahoo.slime.SlimeUtils;

/**
 * Wireguard parameters for a tenant host/node.
 *
 * @author gjoranv
 */
public record TenantParameters(String hostname, WireguardKey publicKey) {

    public static TenantParameters fromJson(String json) {
        Slime slime = SlimeUtils.jsonToSlime(json);
        Cursor root = slime.get();
        return new TenantParameters(
                root.field("hostname").asString(),
                WireguardKey.from(root.field("publicKey").asString())
        );
    }

    public String toJson() {
        Slime slime = new Slime();
        Cursor cursor = slime.setObject();
        cursor.setString("hostname", hostname);
        cursor.setString("publicKey", publicKey.value());
        return SlimeUtils.toJson(slime);
    }

}
