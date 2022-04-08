// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.dns;

import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.zone.ZoneId;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * @author mpolden
 */
public class AliasTargetTest {

    @Test
    public void packing() {
        List<AliasTarget> tests = List.of(
                new LatencyAliasTarget(HostName.from("foo.example.com"), "dns-zone-1", ZoneId.from("prod.us-north-1")),
                new WeightedAliasTarget(HostName.from("bar.example.com"), "dns-zone-2", ZoneId.from("prod.us-north-2"), 50)
        );
        for (var target : tests) {
            AliasTarget unpacked = AliasTarget.unpack(target.pack());
            assertEquals(target, unpacked);
        }

        List<RecordData> invalidData = List.of(RecordData.from(""), RecordData.from("foobar"));
        for (var data : invalidData) {
            try {
                AliasTarget.unpack(data);
                fail("Expected exception");
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

}
