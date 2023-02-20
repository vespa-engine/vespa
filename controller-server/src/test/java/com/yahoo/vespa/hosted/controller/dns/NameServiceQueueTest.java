// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.dns;

import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.api.integration.dns.AliasTarget;
import com.yahoo.vespa.hosted.controller.api.integration.dns.DirectTarget;
import com.yahoo.vespa.hosted.controller.api.integration.dns.LatencyAliasTarget;
import com.yahoo.vespa.hosted.controller.api.integration.dns.MemoryNameService;
import com.yahoo.vespa.hosted.controller.api.integration.dns.NameService;
import com.yahoo.vespa.hosted.controller.api.integration.dns.Record;
import com.yahoo.vespa.hosted.controller.api.integration.dns.Record.Type;
import com.yahoo.vespa.hosted.controller.api.integration.dns.RecordData;
import com.yahoo.vespa.hosted.controller.api.integration.dns.RecordName;
import com.yahoo.vespa.hosted.controller.application.TenantAndApplicationId;
import com.yahoo.vespa.hosted.controller.dns.NameServiceQueue.Priority;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
        var o0 = Optional.<TenantAndApplicationId>empty();
        var o1 = Optional.of(TenantAndApplicationId.from("t", "a"));
        var o2 = Optional.of(TenantAndApplicationId.from("t", "b"));
        var req1 = new CreateRecord(o0, r1);
        var req2 = new CreateRecords(o1, List.of(r2));
        var req3 = new CreateRecords(o2, r3);
        var req4 = new RemoveRecords(o0, r3.get(0).type(), r3.get(0).name());
        var req5 = new RemoveRecords(o1, r2.type(), r2.name(), r2.data());
        var req6 = new RemoveRecords(o2, Record.Type.CNAME, r1.name(), r1.data());

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

        // Remove some requests
        queue = new NameServiceQueue(List.of(req1, req2, req2, req3)).without(new NameServiceQueue(List.of(req1, req2)));
        assertEquals(List.of(req2, req3), queue.requests());
    }

    @Test
    void test_failing_requests() {
        Deque<Consumer<RecordName>> expectations = new ArrayDeque<>();
        var nameService = new NameService() {
            @Override public Record createRecord(Type type, RecordName name, RecordData data) {
                var expectation = expectations.poll();
                assertNotNull(expectation, "unexpected dispatch; add more expectations, or fix the bug!");
                expectation.accept(name);
                return null;
            }
            @Override public List<Record> createAlias(RecordName name, Set<AliasTarget> targets) { throw new UnsupportedOperationException(); }
            @Override public List<Record> createDirect(RecordName name, Set<DirectTarget> targets) { throw new UnsupportedOperationException(); }
            @Override public List<Record> createTxtRecords(RecordName name, List<RecordData> txtRecords) { throw new UnsupportedOperationException(); }
            @Override public List<Record> findRecords(Type type, RecordName name) { return List.of(); }
            @Override public void updateRecord(Record record, RecordData newData) { throw new UnsupportedOperationException(); }
            @Override public void removeRecords(List<Record> record) { throw new UnsupportedOperationException(); }
        };

        var owner0 = Optional.<TenantAndApplicationId>empty();
        var owner1 = Optional.of(TenantAndApplicationId.from("t", "a"));
        var owner2 = Optional.of(TenantAndApplicationId.from("t", "b"));

        var rec1 = new Record(Type.A, RecordName.from("one"), RecordData.from("data"));
        var rec2 = new Record(Type.A, RecordName.from("two"), RecordData.from("data"));
        var rec3 = new Record(Type.A, RecordName.from("three"), RecordData.from("data"));
        var rec4 = new Record(Type.A, RecordName.from("four"), RecordData.from("data"));

        var req1 = new CreateRecord(owner0, rec1);
        var req2 = new CreateRecord(owner1, rec2);
        var req3 = new CreateRecord(owner1, rec3);
        var req4 = new CreateRecord(owner2, rec4);
        var req5 = new CreateRecord(owner1, rec4);
        var req6 = new CreateRecord(owner0, rec1);
        var req7 = new CreateRecord(owner1, rec4);
        var req8 = new CreateRecord(owner0, rec2);

        var base = List.<NameServiceRequest>of(req1, req2, req3, req4, req5, req6, req7, req8);

        // failing operator request, nothing happens repeatedly
        RuntimeException exception = new RuntimeException();
        for (int i = 0; i < 4; i++) expectations.add(name -> { assertEquals(rec1.name(), name); throw exception; });
        assertEquals(base,
                     new NameServiceQueue(base).dispatchTo(nameService, 4).requests());
        assertEquals(0, expectations.size());

        // operator request OK, owner1 fails first request, owner2 is moved first in the queue, but not yet dispatched
        expectations.add(name -> { assertEquals(rec1.name(), name); });
        expectations.add(name -> { assertEquals(rec2.name(), name); throw exception; });
        assertEquals(List.of(req4, req2, req3, req5, req6, req7, req8),
                     new NameServiceQueue(base).dispatchTo(nameService, 2).requests());
        assertEquals(0, expectations.size());

        // operator request OK, owner1 fails first request, then owner2 gets to run one request, then owner1's first request is attempted repeatedly
        expectations.add(name -> { assertEquals(rec1.name(), name); });
        expectations.add(name -> { assertEquals(rec2.name(), name); throw exception; });
        expectations.add(name -> { assertEquals(rec4.name(), name); });
        expectations.add(name -> { assertEquals(rec2.name(), name); throw exception; });
        expectations.add(name -> { assertEquals(rec2.name(), name); throw exception; });
        assertEquals(List.of(req2, req3, req5, req6, req7, req8),
                     new NameServiceQueue(base).dispatchTo(nameService, 5).requests());
        assertEquals(0, expectations.size());

        expectations.add(name -> { assertEquals(rec1.name(), name); throw exception; });    // operator fails
        expectations.add(name -> { assertEquals(rec1.name(), name); });                     // operator succeeds
        expectations.add(name -> { assertEquals(rec2.name(), name); });                     // owner1 succeeds
        expectations.add(name -> { assertEquals(rec3.name(), name); throw exception; });    // owner1 fails
        expectations.add(name -> { assertEquals(rec4.name(), name); throw exception; });    // owner2 fails
        expectations.add(name -> { assertEquals(rec3.name(), name); throw exception; });    // owner1 fails
        expectations.add(name -> { assertEquals(rec4.name(), name); });                     // owner2 succeeds
        expectations.add(name -> { assertEquals(rec3.name(), name); });                     // owner1 succeeds
        expectations.add(name -> { assertEquals(rec4.name(), name); });                     // owner1 succeeds
        expectations.add(name -> { assertEquals(rec1.name(), name); throw exception; });    // operator fails
        expectations.add(name -> { assertEquals(rec1.name(), name); });                     // operator succeeds
        expectations.add(name -> { assertEquals(rec4.name(), name); throw exception; });    // owner1 fails
        expectations.add(name -> { assertEquals(rec4.name(), name); });                     // owner1 succeeds
        expectations.add(name -> { assertEquals(rec2.name(), name); });                     // operator succeeds
        assertEquals(List.of(),
                     new NameServiceQueue(base).dispatchTo(nameService, 100).requests());
        assertEquals(0, expectations.size());

        // Finally, let the queue fill past its capacity, and see that failed requests are simply dropped instead.
        expectations.add(name -> { assertEquals(rec1.name(), name); throw exception; });
        expectations.add(name -> { assertEquals(rec2.name(), name); });
        expectations.add(name -> { assertEquals(rec1.name(), name); throw exception; });
        expectations.add(name -> { assertEquals(rec2.name(), name); });
        var full = new LinkedList<NameServiceRequest>();
        for (int i = 0; i < NameServiceQueue.QUEUE_CAPACITY; i++) {
            full.add(req1);
            full.add(req2);
        }
        assertEquals(full.subList(4, full.size()),
                     new NameServiceQueue(full).dispatchTo(nameService, 4).requests());

        // However, if the queue is even fuller, at the end of a dispatch run, the oldest requests are discarded too.
        full.add(req3);
        full.add(req4);
        assertEquals(full.subList(2, full.size()),
                     new NameServiceQueue(full).dispatchTo(nameService, 0).requests());
    }

}
