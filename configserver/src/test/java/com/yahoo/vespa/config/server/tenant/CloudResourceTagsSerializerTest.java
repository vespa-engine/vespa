// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.tenant;

import com.yahoo.config.provision.CloudResourceTags;
import com.yahoo.slime.Slime;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author gjoranv
 */
public class CloudResourceTagsSerializerTest {

    @Test
    public void roundTrip() {
        var tags = CloudResourceTags.from(Map.of("env", "prod", "team", "search", "cost-center", "42"));
        var slime = new Slime();
        CloudResourceTagsSerializer.toSlime(tags, slime.setObject());
        var deserialized = CloudResourceTagsSerializer.fromSlime(slime.get());
        assertEquals(tags, deserialized);
    }

    @Test
    public void emptyRoundTrip() {
        var slime = new Slime();
        CloudResourceTagsSerializer.toSlime(CloudResourceTags.empty(), slime.setObject());
        var deserialized = CloudResourceTagsSerializer.fromSlime(slime.get());
        assertTrue(deserialized.isEmpty());
    }

    @Test
    public void fromEmptyInspector() {
        var slime = new Slime();
        slime.setObject();
        var deserialized = CloudResourceTagsSerializer.fromSlime(slime.get());
        assertTrue(deserialized.isEmpty());
    }

    @Test
    public void emptyValuePreserved() {
        var tags = CloudResourceTags.from(Map.of("marker", ""));
        var slime = new Slime();
        CloudResourceTagsSerializer.toSlime(tags, slime.setObject());
        var deserialized = CloudResourceTagsSerializer.fromSlime(slime.get());
        assertEquals("", deserialized.asMap().get("marker"));
    }

}
