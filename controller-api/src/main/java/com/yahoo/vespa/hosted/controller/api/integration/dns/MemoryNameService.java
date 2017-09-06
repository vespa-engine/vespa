// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.dns;


import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * An in-memory name service for testing purposes.
 *
 * @author mpolden
 */
public class MemoryNameService implements NameService {

    private final List<Record> records = new ArrayList<>();

    public List<Record> records() {
        return Collections.unmodifiableList(records);
    }

    @Override
    public RecordId createCname(String alias, String canonicalName) {
        records.add(new Record(Record.Type.CNAME.name(), alias, canonicalName));
        return new RecordId(UUID.randomUUID().toString());
    }

    @Override
    public Optional<Record> findRecord(Record.Type type, String name) {
        return records.stream()
                .filter(record -> record.type() == type && record.name().equals(name))
                .findFirst();
    }
}
