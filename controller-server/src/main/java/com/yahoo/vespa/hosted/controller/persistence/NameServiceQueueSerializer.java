// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.persistence;


import com.yahoo.slime.ArrayTraverser;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.Slime;
import com.yahoo.slime.SlimeUtils;
import com.yahoo.vespa.hosted.controller.api.integration.dns.Record;
import com.yahoo.vespa.hosted.controller.api.integration.dns.RecordData;
import com.yahoo.vespa.hosted.controller.api.integration.dns.RecordName;
import com.yahoo.vespa.hosted.controller.application.TenantAndApplicationId;
import com.yahoo.vespa.hosted.controller.dns.CreateRecord;
import com.yahoo.vespa.hosted.controller.dns.CreateRecords;
import com.yahoo.vespa.hosted.controller.dns.NameServiceQueue;
import com.yahoo.vespa.hosted.controller.dns.NameServiceRequest;
import com.yahoo.vespa.hosted.controller.dns.RemoveRecords;

import java.util.ArrayList;
import java.util.Optional;

/**
 * Serializer for {@link com.yahoo.vespa.hosted.controller.dns.NameServiceQueue}.
 *
 * @author mpolden
 */
public class NameServiceQueueSerializer {

    // WARNING: Since there are multiple servers in a ZooKeeper cluster and they upgrade one by one
    //          (and rewrite all nodes on startup), changes to the serialized format must be made
    //          such that what is serialized on version N+1 can be read by version N:
    //          - ADDING FIELDS: Always ok
    //          - REMOVING FIELDS: Stop reading the field first. Stop writing it on a later version.
    //          - CHANGING THE FORMAT OF A FIELD: Don't do it bro.

    private static final String requestsField = "requests";
    private static final String requestType = "requestType";
    private static final String recordsField = "records";
    private static final String typeField = "type";
    private static final String nameField = "name";
    private static final String dataField = "data";
    private static final String ownerField = "owner";

    public Slime toSlime(NameServiceQueue queue) {
        var slime = new Slime();
        var root = slime.setObject();
        var array = root.setArray(requestsField);

        for (var request : queue.requests()) {
            var object = array.addObject();

            request.owner().ifPresent(owner -> object.setString(ownerField, owner.serialized()));

            if (request instanceof CreateRecords) toSlime(object, (CreateRecords) request);
            else if (request instanceof CreateRecord) toSlime(object, (CreateRecord) request);
            else if (request instanceof RemoveRecords) toSlime(object, (RemoveRecords) request);
            else throw new IllegalArgumentException("No serialization defined for request of type " +
                                                    request.getClass().getName());
        }

        return slime;
    }

    public NameServiceQueue fromSlime(Slime slime) {
        var items = new ArrayList<NameServiceRequest>();
        var root = slime.get();
        root.field(requestsField).traverse((ArrayTraverser) (i, object) -> {
            Optional<TenantAndApplicationId> owner = SlimeUtils.optionalString(object.field(ownerField)).map(TenantAndApplicationId::fromSerialized);
            var request = Request.valueOf(object.field(requestType).asString());
            switch (request) {
                case createRecords -> items.add(createRecordsFromSlime(object, owner));
                case createRecord -> items.add(createRecordFromSlime(object, owner));
                case removeRecords -> items.add(removeRecordsFromSlime(object, owner));
                default -> throw new IllegalArgumentException("No serialization defined for request " + request);
            }
        });
        return new NameServiceQueue(items);
    }

    private void toSlime(Cursor object, CreateRecord createRecord) {
        object.setString(requestType, Request.createRecord.name());
        toSlime(object, createRecord.record());
    }

    private void toSlime(Cursor object, CreateRecords createRecords) {
        object.setString(requestType, Request.createRecords.name());
        var recordArray = object.setArray(recordsField);
        createRecords.records().forEach(record -> toSlime(recordArray.addObject(), record));
    }

    private void toSlime(Cursor object, RemoveRecords removeRecords) {
        object.setString(requestType, Request.removeRecords.name());
        object.setString(typeField, removeRecords.type().name());
        object.setString(nameField, removeRecords.name().asString());
        removeRecords.data().ifPresent(data -> object.setString(dataField, data.asString()));
    }

    private void toSlime(Cursor object, Record record) {
        object.setString(typeField, record.type().name());
        object.setString(nameField, record.name().asString());
        object.setString(dataField, record.data().asString());
    }

    private CreateRecords createRecordsFromSlime(Inspector object, Optional<TenantAndApplicationId> owner) {
        var records = new ArrayList<Record>();
        object.field(recordsField).traverse((ArrayTraverser) (i, recordObject) -> records.add(recordFromSlime(recordObject)));
        return new CreateRecords(owner, records);
    }

    private CreateRecord createRecordFromSlime(Inspector object, Optional<TenantAndApplicationId> owner) {
        return new CreateRecord(owner, recordFromSlime(object));
    }

    private RemoveRecords removeRecordsFromSlime(Inspector object, Optional<TenantAndApplicationId> owner) {
        var type = Record.Type.valueOf(object.field(typeField).asString());
        var name = RecordName.from(object.field(nameField).asString());
        var data = SlimeUtils.optionalString(object.field(dataField)).map(RecordData::from);
        return new RemoveRecords(owner, type, name, data);
    }

    private Record recordFromSlime(Inspector object) {
        return new Record(Record.Type.valueOf(object.field(typeField).asString()),
                          RecordName.from(object.field(nameField).asString()),
                          RecordData.from(object.field(dataField).asString()));
    }

    private enum Request {
        createRecord,
        createRecords,
        removeRecords,
    }

}
