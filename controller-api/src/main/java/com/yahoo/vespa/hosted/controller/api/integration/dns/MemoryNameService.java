// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.dns;


import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
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

    public void add(Record record) {
        Optional<Record> conflict = records.stream().filter(r -> conflicts(r, record)).findFirst();
        if (conflict.isPresent()) {
            throw new AssertionError("'" + record + "' conflicts with existing record '" +
                                     conflict.get() + "'");
        }
        records.add(record);
    }

    @Override
    public Record createRecord(Record.Type type, RecordName name, RecordData canonicalName) {
        var record = new Record(type, name, canonicalName);
        add(record);
        return record;
    }

    @Override
    public List<Record> createAlias(RecordName name, Set<AliasTarget> targets) {
        var records = targets.stream()
                             .sorted((a, b) -> Comparator.comparing(AliasTarget::name).compare(a, b))
                             .map(d -> new Record(Record.Type.ALIAS, name, d.pack()))
                             .collect(Collectors.toList());
        // Satisfy idempotency contract of interface
        for (var r1 : records) {
            this.records.removeIf(r2 -> conflicts(r1, r2));
        }
        this.records.addAll(records);
        return records;
    }

    @Override
    public List<Record> createDirect(RecordName name, Set<DirectTarget> targets) {
        var records = targets.stream()
                .sorted((a, b) -> Comparator.comparing((DirectTarget target) -> target.recordData().asString()).compare(a, b))
                .map(d -> new Record(Record.Type.DIRECT, name, d.pack()))
                .collect(Collectors.toList());
        // Satisfy idempotency contract of interface
        for (var r1 : records) {
            this.records.removeIf(r2 -> conflicts(r1, r2));
        }
        this.records.addAll(records);
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
                                  return RecordData.fqdn(AliasTarget.unpack(record.data()).name().value())
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
        records.forEach(this.records::remove);
    }

    /**
     * Returns whether record r1 and r2 are in conflict. This attempts to enforce the same constraints a
     * most real name services.
     */
    private static boolean conflicts(Record r1, Record r2) {
        if (!r1.name().equals(r2.name())) return false;                // Distinct names never conflict
        if (r1.type() == Record.Type.ALIAS && r1.type() == r2.type()) {
            AliasTarget t1 = AliasTarget.unpack(r1.data());
            AliasTarget t2 = AliasTarget.unpack(r2.data());
            return t1.name().equals(t2.name());                        // ALIAS records require distinct targets
        }
        if (r1.type() == Record.Type.DIRECT && r1.type() == r2.type()) {
            DirectTarget t1 = DirectTarget.unpack(r1.data());
            DirectTarget t2 = DirectTarget.unpack(r2.data());
            return t1.id().equals(t2.id());                            // DIRECT records require distinct IDs
        }
        return true;                                                   // Anything else is considered a conflict
    }

}
