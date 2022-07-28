// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.dns;

import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.api.integration.dns.LatencyAliasTarget;
import com.yahoo.vespa.hosted.controller.api.integration.dns.MemoryNameService;
import com.yahoo.vespa.hosted.controller.api.integration.dns.Record;
import com.yahoo.vespa.hosted.controller.api.integration.dns.RecordData;
import com.yahoo.vespa.hosted.controller.api.integration.dns.RecordName;
import com.yahoo.vespa.hosted.controller.dns.NameServiceQueue.Priority;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author mpolden
 */
public class NameServiceQueueTest {

    @Test
    void test_queue() {
        var nameService = new MemoryNameService();
        var r1 = new Record(Record.Type.CNAME, RecordName.from("cname.vespa.oath.cloud"), RecordData.from("example.com"));
        var r2 = new Record(Record.Type.TXT, RecordName.from("txt.example.com"), RecordData.from("text"));
        var r3 = List.of(new Record(Record.Type.ALIAS, RecordName.from("alias.example.com"),
                        new LatencyAliasTarget(HostName.of("alias1"),
                                "dns-zone-01",
                                ZoneId.from("prod", "us-north-1")).pack()),
                new Record(Record.Type.ALIAS, RecordName.from("alias.example.com"),
                        new LatencyAliasTarget(HostName.of("alias2"),
                                "dns-zone-02",
                                ZoneId.from("prod", "us-north-2")).pack()));
        var req1 = new CreateRecord(r1);
        var req2 = new CreateRecords(List.of(r2));
        var req3 = new CreateRecords(r3);
        var req4 = new RemoveRecords(r3.get(0).type(), r3.get(0).name());
        var req5 = new RemoveRecords(r2.type(), r2.data());
        var req6 = new RemoveRecords(Record.Type.CNAME, r1.data());

        // Add requests with different priorities and dispatch first one
        var queue = NameServiceQueue.EMPTY.with(req2).with(req1, Priority.high);
        assertEquals(2, queue.requests().size());
        queue = queue.dispatchTo(nameService, 1);
        assertEquals(r1, nameService.findRecords(r1.type(), r1.name()).get(0));
        assertEquals(1, queue.requests().size());
        assertEquals(req2, queue.requests().iterator().next());

        // Dispatch remaining requests
        queue = queue.dispatchTo(nameService, 10);
        assertTrue(queue.requests().isEmpty());
        assertEquals(r2, nameService.findRecords(r2.type(), r2.name()).get(0));

        // Dispatch from empty queue
        assertSame(queue, queue.dispatchTo(nameService, 10));

        // Dispatch create alias
        queue = queue.with(req3).dispatchTo(nameService, 1);
        assertEquals(r3, nameService.findRecords(Record.Type.ALIAS, r3.get(0).name()));

        // Dispatch removals
        queue = queue.with(req4).with(req5).dispatchTo(nameService, 2);
        assertTrue(nameService.findRecords(r2.type(), r2.name()).isEmpty(), "Removed " + r2);
        assertTrue(nameService.findRecords(Record.Type.ALIAS, r3.get(0).name()).isEmpty(), "Removed " + r3);

        // Dispatch removals by data
        queue = queue.with(req6).dispatchTo(nameService, 1);
        assertTrue(queue.requests().isEmpty());
        assertTrue(nameService.findRecords(Record.Type.CNAME, r1.name()).isEmpty(), "Removed " + r1);

        // Keep n last requests
        queue = queue.with(req1).with(req2).with(req3).with(req4).with(req6)
                .last(2);
        assertEquals(List.of(req4, req6), List.copyOf(queue.requests()));
        assertSame(queue, queue.last(2));
        assertSame(queue, queue.last(10));
        assertTrue(queue.last(0).requests().isEmpty());

        // Keep n first requests
        queue = NameServiceQueue.EMPTY.with(req1).with(req2).with(req3).with(req4).with(req6)
                .first(3);
        assertEquals(List.of(req1, req2, req3), List.copyOf(queue.requests()));
        assertSame(queue, queue.first(3));
        assertSame(queue, queue.first(10));
        assertTrue(queue.first(0).requests().isEmpty());
    }

}
