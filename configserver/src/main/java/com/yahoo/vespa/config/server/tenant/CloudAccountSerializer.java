// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.tenant;

import com.yahoo.config.provision.CloudAccount;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.Slime;

/**
 * @author mpolden
 */
public class CloudAccountSerializer {

    private static final String ID_FIELD = "id";

    private CloudAccountSerializer() {}

    public static CloudAccount fromSlime(Inspector object) {
        return CloudAccount.from(object.field(ID_FIELD).asString());
    }

    public static Slime toSlime(CloudAccount account) {
        Slime slime = new Slime();
        Cursor root = slime.setObject();
        root.setString(ID_FIELD, account.value());
        return slime;
    }

}
