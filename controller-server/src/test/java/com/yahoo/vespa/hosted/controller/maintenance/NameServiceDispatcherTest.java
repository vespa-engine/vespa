package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.vespa.hosted.controller.ControllerTester;
import com.yahoo.vespa.hosted.controller.api.integration.dns.AliasTarget;
import com.yahoo.vespa.hosted.controller.api.integration.dns.DirectTarget;
import com.yahoo.vespa.hosted.controller.api.integration.dns.NameService;
import com.yahoo.vespa.hosted.controller.api.integration.dns.Record;
import com.yahoo.vespa.hosted.controller.api.integration.dns.Record.Type;
import com.yahoo.vespa.hosted.controller.api.integration.dns.RecordData;
import com.yahoo.vespa.hosted.controller.api.integration.dns.RecordName;
import com.yahoo.vespa.hosted.controller.application.TenantAndApplicationId;
import com.yahoo.vespa.hosted.controller.dns.CreateRecord;
import com.yahoo.vespa.hosted.controller.dns.NameServiceQueue;
import com.yahoo.vespa.hosted.controller.dns.NameServiceQueue.Priority;
import com.yahoo.vespa.hosted.controller.dns.NameServiceRequest;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author jonmv
 */
class NameServiceDispatcherTest {

    @Test
    void testDispatch() {
        Deque<Consumer<RecordName>> expectations = new ArrayDeque<>();
        var nameService = new NameService() {
            @Override public Record createRecord(Type type, RecordName name, RecordData data) { expectations.pop().accept(name); return null; }
            @Override public List<Record> createAlias(RecordName name, Set<AliasTarget> targets) { throw new UnsupportedOperationException(); }
            @Override public List<Record> createDirect(RecordName name, Set<DirectTarget> targets) { throw new UnsupportedOperationException(); }
            @Override public List<Record> createTxtRecords(RecordName name, List<RecordData> txtRecords) { throw new UnsupportedOperationException(); }
            @Override public List<Record> findRecords(Type type, RecordName name) { return List.of(); }
            @Override public void updateRecord(Record record, RecordData newData) { throw new UnsupportedOperationException(); }
            @Override public void removeRecords(List<Record> record) { throw new UnsupportedOperationException(); }
        };
        ControllerTester tester = new ControllerTester();
        NameServiceDispatcher dispatcher = new NameServiceDispatcher(tester.controller(), nameService, Duration.ofMinutes(1));

        var owner0 = Optional.<TenantAndApplicationId>empty();
        var owner1 = Optional.of(TenantAndApplicationId.from("t", "a"));
        var owner2 = Optional.of(TenantAndApplicationId.from("t", "b"));

        var rec1 = new Record(Type.A, RecordName.from("one"), RecordData.from("data"));
        var rec2 = new Record(Type.A, RecordName.from("two"), RecordData.from("data"));
        var rec3 = new Record(Type.A, RecordName.from("three"), RecordData.from("data"));
        var rec4 = new Record(Type.A, RecordName.from("four"), RecordData.from("data"));

        var req1 = new CreateRecord(owner0, rec1);
        var req2 = new CreateRecord(owner1, rec2);
        var req3 = new CreateRecord(owner0, rec1);
        var req4 = new CreateRecord(owner1, rec4);
        var req5 = new CreateRecord(owner2, rec3);
        var req6 = new CreateRecord(owner1, rec4);
        var req7 = new CreateRecord(owner0, rec1);
        var req8 = new CreateRecord(owner0, rec3);

        // Queue initially contains a subsequence of requests, 3–6.
        var base = new LinkedList<NameServiceRequest>(List.of(req3, req4, req5, req6));

        // Whole queue is consumed by dispatcher the first time, and a few records prepended.
        tester.curator().writeNameServiceQueue(new NameServiceQueue(base));
        expectations.add(name -> assertEquals(rec1.name(), name));
        expectations.add(name -> assertEquals(rec4.name(), name));
        expectations.add(name -> assertEquals(rec3.name(), name));
        expectations.add(name -> {
            assertEquals(rec4.name(), name);
            tester.curator().writeNameServiceQueue(tester.curator().readNameServiceQueue()
                                                         .with(req1, Priority.high)
                                                         .with(req2, Priority.high)
                                                         .with(req1, Priority.high));
        });
        assertEquals(1.0, dispatcher.maintain());
        assertEquals(List.of(req1, req2, req1), tester.curator().readNameServiceQueue().requests());

        // Now, the dispatch of requests owned by owner1 will fail, so the remaining list is subsequence or the original.
        tester.curator().writeNameServiceQueue(new NameServiceQueue(base));
        AtomicReference<Consumer<RecordName>> failOwner1 = new AtomicReference<>();
        failOwner1.set(name -> {
            assertEquals(rec4.name(), name);
            expectations.add(failOwner1.get());
            throw new RuntimeException("test error");
        });
        expectations.add(name -> assertEquals(rec1.name(), name));
        expectations.add(failOwner1.get()); // Recursively adds itself to the tail of the expectation queue.
        expectations.add(name -> assertEquals(rec3.name(), name));
        assertEquals(0.5, dispatcher.maintain()); // 2 of 4 requests were ok (the fourth was never attempted).
        assertEquals(List.of(req4, req6), tester.curator().readNameServiceQueue().requests());

        // Queue again initially contains a subsequence of requests, 3–6.
        // While the dispatcher is working through those, some requests are appended, and some are prepended.
        // The dispatch of requests owned by owner1 will fail, so the original sublist read by the dispatcher
        // is not removed, but replaced by a subsequence, upon dispatch end.
        tester.curator().writeNameServiceQueue(new NameServiceQueue(base));
        expectations.clear();
        expectations.add(name -> {
            assertEquals(rec1.name(), name);
            tester.curator().writeNameServiceQueue(tester.curator().readNameServiceQueue()
                                                         .with(req7)
                                                         .with(req2, Priority.high)
                                                         .with(req1, Priority.high)
                                                         .with(req8));
            assertEquals(List.of(req1, req2, req3, req4, req5, req6, req7, req8), tester.curator().readNameServiceQueue().requests());
        });
        expectations.add(failOwner1.get()); // Recursively adds itself to the tail of the expectation queue.
        expectations.add(name -> assertEquals(rec3.name(), name));
        assertEquals(0.5, dispatcher.maintain()); // 2 of 4 requests were ok (the fourth was never attempted).
        assertEquals(List.of(req1, req2, req4, req6, req7, req8), tester.curator().readNameServiceQueue().requests());


        // Finally, queue again initially contains a subsequence of requests, 3–6.
        // While the dispatcher is working through those, the queue is altered in unexpected ways—specifically, owner2's requests is gone.
        // The dispatch of requests owned by owner1 still fail, so the original sublist read by the dispatcher
        // is not removed, nor is it replaced by a subsequence, but the processed requests are attempted removed from the current queue.
        tester.curator().writeNameServiceQueue(new NameServiceQueue(base));
        expectations.clear();
        expectations.add(name -> {
            assertEquals(rec1.name(), name);
            tester.curator().writeNameServiceQueue(new NameServiceQueue(List.of(req1, req2, req3, req4, req6, req7, req8)));
        });
        expectations.add(failOwner1.get()); // Recursively adds itself to the tail of the expectation queue.
        expectations.add(name -> assertEquals(rec3.name(), name));
        assertEquals(0.5, dispatcher.maintain()); // 2 of 4 requests were ok (the fourth was never attempted).
        assertEquals(List.of(req2, req3, req4, req6, req7, req8), tester.curator().readNameServiceQueue().requests());
    }

}
