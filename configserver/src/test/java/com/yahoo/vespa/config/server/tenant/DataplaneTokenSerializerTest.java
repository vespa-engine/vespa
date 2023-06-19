// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.tenant;

import com.yahoo.config.provision.DataplaneToken;
import com.yahoo.slime.Slime;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * @author mortent
 */
public class DataplaneTokenSerializerTest {

    @Test
    public void testSerialization() {
        List<DataplaneToken> tokens = List.of(
                new DataplaneToken("id1",
                                   List.of(new DataplaneToken.Version("id1_fingerPrint1", "id1_checkaccesshash1"))),
                new DataplaneToken("id2",
                                   List.of(new DataplaneToken.Version("id2_fingerPrint1", "id2_checkaccesshash1"),
                                           new DataplaneToken.Version("id3_fingerPrint1", "id3_checkaccesshash1"))));
        Slime slime = DataplaneTokenSerializer.toSlime(tokens);
        List<DataplaneToken> deserialized = DataplaneTokenSerializer.fromSlime(slime.get());
        assertEquals(tokens, deserialized);
    }
}
