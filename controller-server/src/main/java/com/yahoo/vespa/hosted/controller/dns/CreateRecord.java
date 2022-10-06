// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.dns;

import com.yahoo.vespa.hosted.controller.api.integration.dns.NameService;
import com.yahoo.vespa.hosted.controller.api.integration.dns.Record;

import java.util.List;
import java.util.Objects;

/**
 * Create or update a record.
 *
 * @author mpolden
 */
public class CreateRecord implements NameServiceRequest {

    private final Record record;

    /** DO NOT USE. Public for serialization purposes */
    public CreateRecord(Record record) {
        this.record = Objects.requireNonNull(record, "record must be non-null");
        if (record.type() != Record.Type.CNAME && record.type() != Record.Type.A) {
            throw new IllegalArgumentException("Record of type " + record.type() + " is not supported: " + record);
        }
    }

    public Record record() {
        return record;
    }

    @Override
    public void dispatchTo(NameService nameService) {
        List<Record> records = nameService.findRecords(record.type(), record.name());
        records.forEach(r -> {
            // Ensure that existing record has correct data
            if (!r.data().equals(record.data())) {
                nameService.updateRecord(r, record.data());
            }
        });
        if (records.isEmpty()) {
            nameService.createRecord(record.type(), record.name(), record.data());
        }
    }

    @Override
    public String toString() {
        return "create record " + record;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CreateRecord that = (CreateRecord) o;
        return record.equals(that.record);
    }

    @Override
    public int hashCode() {
        return Objects.hash(record);
    }

}
