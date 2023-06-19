// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.tenant;

import com.yahoo.config.provision.DataplaneToken;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.Slime;
import com.yahoo.slime.SlimeUtils;

import java.util.List;

/**
 * Serialize/deserialize dataplane tokens
 *
 * @author mortent
 */
public class DataplaneTokenSerializer {

    private static final String ID_FIELD = "id";
    private static final String VERSIONS_FIELD = "versions";
    private static final String FINGERPRINT_FIELD = "fingerPrint";
    private static final String CHECKACCESSHASH_FIELD = "checkAccessHash";

    private DataplaneTokenSerializer() {}

    public static List<DataplaneToken> fromSlime(Inspector object) {
        return SlimeUtils.entriesStream(object)
                .map(DataplaneTokenSerializer::tokenFromSlime)
                .toList();
    }

    private static DataplaneToken tokenFromSlime(Inspector object) {
        String id = object.field(ID_FIELD).asString();
        List<DataplaneToken.Version> versions = SlimeUtils.entriesStream(object.field(VERSIONS_FIELD))
                .filter(Inspector::valid)
                .map(DataplaneTokenSerializer::tokenValue)
                .toList();
        return new DataplaneToken(id, versions);
    }

    private static DataplaneToken.Version tokenValue(Inspector inspector) {
        return new DataplaneToken.Version(
                inspector.field(FINGERPRINT_FIELD).asString(),
                inspector.field(CHECKACCESSHASH_FIELD).asString());
    }

    public static Slime toSlime(List<DataplaneToken> dataplaneTokens) {
        Slime slime = new Slime();
        Cursor root = slime.setArray();
        for (DataplaneToken token : dataplaneTokens) {
            Cursor cursor = root.addObject();
            cursor.setString(ID_FIELD, token.tokenId());
            Cursor versions = cursor.setArray(VERSIONS_FIELD);
            token.versions().forEach(v -> {
                Cursor val = versions.addObject();
                val.setString(FINGERPRINT_FIELD, v.fingerprint());
                val.setString(CHECKACCESSHASH_FIELD, v.checkAccessHash());
            });
        }
        return slime;
    }
}
