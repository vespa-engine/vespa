// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.dns;


import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * An in-memory name service for testing purposes.
 *
 * @author mpolden
 */
public class MemoryNameService implements NameService {

    private final Map<RecordId, Record> records = new HashMap<>();

    public Map<RecordId, Record> records() {
        return Collections.unmodifiableMap(records);
    }

    @Override
    public RecordId createCname(RecordName alias, RecordData canonicalName) {
        RecordId id = new RecordId(UUID.randomUUID().toString());
        records.put(id, new Record(id, Record.Type.CNAME, alias, canonicalName));
        return id;
    }

    @Override
    public Optional<Record> findRecord(Record.Type type, RecordName name) {
        return records.values().stream()
                .filter(record -> record.type() == type && record.name().equals(name))
                .findFirst();
    }

    @Override
    public List<Record> findRecord(Record.Type type, RecordData data) {
        return records.values().stream()
                .filter(record -> record.type() == type && record.data().equals(data))
                .collect(Collectors.toList());
    }

    @Override
    public void updateRecord(RecordId id, RecordData newData) {
        records.computeIfPresent(id, (k, record) -> new Record(id, record.type(), record.name(), newData));
    }

    @Override
    public void removeRecord(RecordId id) {
        records.remove(id);
    }
}
