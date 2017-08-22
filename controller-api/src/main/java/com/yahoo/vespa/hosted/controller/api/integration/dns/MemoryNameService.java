// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.dns;


import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * An in-memory name service for testing purposes.
 *
 * @author mpolden
 */
public class MemoryNameService implements NameService {

    private final Set<Record> records = new HashSet<>();

    @Override
    public RecordId createCname(String alias, String canonicalName) {
        records.add(new Record("CNAME", alias, canonicalName));
        return new RecordId(UUID.randomUUID().toString());
    }

    @Override
    public Optional<Record> findRecord(Record.Type type, String name) {
        return records.stream()
                .filter(record -> record.type() == type && record.name().equals(name))
                .findFirst();
    }
}
