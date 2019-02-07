// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.dns;


import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * An in-memory name service for testing purposes.
 *
 * @author mpolden
 */
public class MemoryNameService implements NameService {

    private final Map<RecordId, Set<Record>> records = new HashMap<>();

    public Map<RecordId, Set<Record>> records() {
        return Collections.unmodifiableMap(records);
    }

    @Override
    public RecordId createCname(RecordName name, RecordData canonicalName) {
        RecordId id = new RecordId(UUID.randomUUID().toString());
        records.put(id, Set.of(new Record(id, Record.Type.CNAME, name, canonicalName)));
        return id;
    }

    @Override
    public RecordId createAlias(RecordName name, Set<AliasTarget> targets) {
        RecordId id = new RecordId(UUID.randomUUID().toString());
        Set<Record> records = targets.stream()
                                     .sorted((a, b) -> Comparator.comparing(AliasTarget::name).compare(a, b))
                                     .map(target -> new Record(id, Record.Type.ALIAS, name,
                                                               RecordData.fqdn(target.name().value())))
                                     .collect(Collectors.toCollection(LinkedHashSet::new));
        // Satisfy idempotency contract of interface
        findRecords(Record.Type.ALIAS, name).stream().map(Record::id).forEach(this::removeRecord);
        this.records.put(id, records);
        return id;
    }

    @Override
    public List<Record> findRecords(Record.Type type, RecordName name) {
        return records.values().stream()
                      .flatMap(Collection::stream)
                      .filter(record -> record.type() == type && record.name().equals(name))
                      .collect(Collectors.toUnmodifiableList());
    }

    @Override
    public List<Record> findRecords(Record.Type type, RecordData data) {
        return records.values().stream()
                      .flatMap(Collection::stream)
                      .filter(record -> record.type() == type && record.data().equals(data))
                      .collect(Collectors.toUnmodifiableList());
    }

    @Override
    public void updateRecord(RecordId id, RecordData newData) {
        records.computeIfPresent(id, (k, records) -> {
            if (records.isEmpty()) {
                throw new IllegalArgumentException("No record with data '" + newData.asString() + "' exists");
            }
            if (records.size() > 1) {
                throw new IllegalArgumentException("Cannot update multi-value record '" + id.asString() + "' with '" +
                                                   newData.asString() + "'");
            }
            Record existing = records.iterator().next();
            return Set.of(new Record(id, existing.type(), existing.name(), newData));
        });
    }

    @Override
    public void removeRecord(RecordId id) {
        records.remove(id);
    }

}
