// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.tenant;

import com.yahoo.config.provision.CloudAccount;
import org.junit.Test;

import static com.yahoo.vespa.config.server.tenant.CloudAccountSerializer.fromSlime;
import static com.yahoo.vespa.config.server.tenant.CloudAccountSerializer.toSlime;
import static org.junit.Assert.assertEquals;

/**
 * @author mpolden
 */
public class CloudAccountSerializerTest {

    @Test
    public void serialization() {
        CloudAccount account = CloudAccount.from("012345678912");
        assertEquals(account, fromSlime(toSlime(account).get()));
    }

}
