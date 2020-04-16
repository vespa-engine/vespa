// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.dns;


import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * An in-memory name service for testing purposes.
 *
 * @author mpolden
 */
public class MemoryNameService implements NameService {

    private final Set<Record> records = new TreeSet<>();

    public Set<Record> records() {
        return Collections.unmodifiableSet(records);
    }

    private void add(Record record) {
        if (records.stream().anyMatch(r -> r.type().equals(record.type()) &&
                                           r.name().equals(record.name()) &&
                                           r.data().equals(record.data()))) {
            throw new IllegalArgumentException("Record already exists: " + record);
        }
        records.add(record);
    }

    @Override
    public Record createCname(RecordName name, RecordData canonicalName) {
        var record = new Record(Record.Type.CNAME, name, canonicalName);
        add(record);
        return record;
    }

    @Override
    public List<Record> createAlias(RecordName name, Set<AliasTarget> targets) {
        var records = targets.stream()
                             .sorted((a, b) -> Comparator.comparing(AliasTarget::name).compare(a, b))
                             .map(target -> new Record(Record.Type.ALIAS, name, target.asData()))
                             .collect(Collectors.toList());
        // Satisfy idempotency contract of interface
        removeRecords(records);
        records.forEach(this::add);
        return records;
    }

    @Override
    public List<Record> createTxtRecords(RecordName name, List<RecordData> txtData) {
        var records = txtData.stream()
                             .map(data -> new Record(Record.Type.TXT, name, data))
                             .collect(Collectors.toList());
        records.forEach(this::add);
        return records;
    }

    @Override
    public List<Record> findRecords(Record.Type type, RecordName name) {
        return records.stream()
                      .filter(record -> record.type() == type && record.name().equals(name))
                      .collect(Collectors.toUnmodifiableList());
    }

    @Override
    public List<Record> findRecords(Record.Type type, RecordData data) {
        if (type == Record.Type.ALIAS && data.asString().contains("/")) {
            // Validate the same expectation as of a real name service
            throw new IllegalArgumentException("Finding " + Record.Type.ALIAS + " record by data should only include " +
                                               "the FQDN name");
        }
        return records.stream()
                      .filter(record -> {
                          if (record.type() == type) {
                              if (type == Record.Type.ALIAS) {
                                  // Unpack ALIAS record and compare FQDN of data part
                                  return RecordData.fqdn(AliasTarget.from(record.data()).name().value())
                                                   .equals(data);
                              }
                              return record.data().equals(data);
                          }
                          return false;
                      })
                      .collect(Collectors.toUnmodifiableList());
    }

    @Override
    public void updateRecord(Record record, RecordData newData) {
        var records = findRecords(record.type(), record.name());
        if (records.isEmpty()) {
            throw new IllegalArgumentException("No record with data '" + newData.asString() + "' exists");
        }
        if (records.size() > 1) {
            throw new IllegalArgumentException("Cannot update multi-value record '" + record.name().asString() +
                                               "' with '" + newData.asString() + "'");
        }
        var existing = records.get(0);
        this.records.remove(existing);
        add(new Record(existing.type(), existing.name(), newData));
    }

    @Override
    public void removeRecords(List<Record> records) {
        this.records.removeAll(records);
    }

}
