// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.dns;

import com.yahoo.transaction.Mutex;
import com.yahoo.vespa.hosted.controller.api.integration.dns.AliasTarget;
import com.yahoo.vespa.hosted.controller.api.integration.dns.DirectTarget;
import com.yahoo.vespa.hosted.controller.api.integration.dns.NameService;
import com.yahoo.vespa.hosted.controller.api.integration.dns.Record;
import com.yahoo.vespa.hosted.controller.api.integration.dns.RecordData;
import com.yahoo.vespa.hosted.controller.api.integration.dns.RecordName;
import com.yahoo.vespa.hosted.controller.maintenance.NameServiceDispatcher;
import com.yahoo.vespa.hosted.controller.persistence.CuratorDb;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * This adds name service requests to the {@link NameServiceQueue}.
 *
 * Name service requests passed to this are not immediately sent to a name service, but are instead persisted
 * in a curator-backed queue. Enqueued requests are later dispatched to a {@link NameService} by
 * {@link NameServiceDispatcher}.
 *
 * @author mpolden
 */
public class NameServiceForwarder {

    /**
     * The number of {@link NameServiceRequest}s we allow to be queued. When the queue overflows, the first requests
     * are dropped in a FIFO order until the queue shrinks below this capacity.
     */
    private static final int QUEUE_CAPACITY = 400;

    private static final Logger log = Logger.getLogger(NameServiceForwarder.class.getName());

    private final CuratorDb db;

    public NameServiceForwarder(CuratorDb db) {
        this.db = Objects.requireNonNull(db, "db must be non-null");
    }

    /** Create or update a given record */
    public void createRecord(Record record, NameServiceQueue.Priority priority) {
        forward(new CreateRecord(record), priority);
    }

    /** Create or update an ALIAS record with given name and targets */
    public void createAlias(RecordName name, Set<AliasTarget> targets, NameServiceQueue.Priority priority) {
        var records = targets.stream()
                             .map(target -> new Record(Record.Type.ALIAS, name, target.pack()))
                             .collect(Collectors.toList());
        forward(new CreateRecords(records), priority);
    }

    /** Create or update a DIRECT record with given name and targets */
    public void createDirect(RecordName name, Set<DirectTarget> targets, NameServiceQueue.Priority priority) {
        var records = targets.stream()
                             .map(target -> new Record(Record.Type.DIRECT, name, target.pack()))
                             .collect(Collectors.toList());
        forward(new CreateRecords(records), priority);
    }

    /** Create or update a TXT record with given name and data */
    public void createTxt(RecordName name, List<RecordData> txtData, NameServiceQueue.Priority priority) {
        var records = txtData.stream()
                             .map(data -> new Record(Record.Type.TXT, name, data))
                             .collect(Collectors.toList());
        forward(new CreateRecords(records), priority);
    }

    /** Remove all records of given type and name */
    public void removeRecords(Record.Type type, RecordName name, NameServiceQueue.Priority priority) {
        forward(new RemoveRecords(type, name), priority);
    }

    /** Remove all records of given type and data */
    public void removeRecords(Record.Type type, RecordData data, NameServiceQueue.Priority priority) {
        forward(new RemoveRecords(type, data), priority);
    }

    /** Remove all records of given type, name and data */
    public void removeRecords(Record.Type type, RecordName name, RecordData data, NameServiceQueue.Priority priority) {
        forward(new RemoveRecords(type, name, data), priority);
    }

    protected void forward(NameServiceRequest request, NameServiceQueue.Priority priority) {
        try (Mutex lock = db.lockNameServiceQueue()) {
            NameServiceQueue queue = db.readNameServiceQueue();
            var queued = queue.requests().size();
            if (queued >= QUEUE_CAPACITY) {
                log.log(Level.WARNING, "Queue is at capacity (size: " + queued + "), dropping older " +
                                          "requests. This likely means that the name service is not successfully " +
                                          "executing requests");
            }
            log.log(Level.FINE, () -> "Queueing name service request: " + request);
            db.writeNameServiceQueue(queue.with(request, priority).last(QUEUE_CAPACITY));
        }
    }

}
