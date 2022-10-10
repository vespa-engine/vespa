// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.dns;

import com.yahoo.config.provision.zone.ZoneId;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author freva
 */
class DirectTargetTest {

    @Test
    void packing() {
        List<DirectTarget> tests = List.of(
                new LatencyDirectTarget(RecordData.from("foo.example.com"), ZoneId.from("prod.us-north-1")),
                new WeightedDirectTarget(RecordData.from("bar.example.com"), ZoneId.from("prod.us-north-2"), 50));
        for (var target : tests) {
            DirectTarget unpacked = DirectTarget.unpack(target.pack());
            assertEquals(target, unpacked);
        }

        List<RecordData> invalidData = List.of(RecordData.from(""), RecordData.from("foobar"));
        for (var data : invalidData) {
            try {
                DirectTarget.unpack(data);
                fail("Expected exception");
            } catch (IllegalArgumentException ignored) { }
        }
    }

}