// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.dns;

import com.yahoo.vespa.hosted.controller.api.integration.dns.AliasTarget;
import com.yahoo.vespa.hosted.controller.api.integration.dns.DirectTarget;
import com.yahoo.vespa.hosted.controller.api.integration.dns.NameService;
import com.yahoo.vespa.hosted.controller.api.integration.dns.Record;
import com.yahoo.vespa.hosted.controller.api.integration.dns.RecordData;
import com.yahoo.vespa.hosted.controller.api.integration.dns.RecordName;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Permanently removes all matching records by type and matching either:
 *
 * - name and data
 * - only name
 * - only data
 *
 * @author mpolden
 */
public class RemoveRecords implements NameServiceRequest {

    private final Record.Type type;
    private final Optional<RecordName> name;
    private final Optional<RecordData> data;

    public RemoveRecords(Record.Type type, RecordName name) {
        this(type, Optional.of(name), Optional.empty());
    }

    public RemoveRecords(Record.Type type, RecordData data) {
        this(type, Optional.empty(), Optional.of(data));
    }

    public RemoveRecords(Record.Type type, RecordName name, RecordData data) {
        this(type, Optional.of(name), Optional.of(data));
    }

    /** DO NOT USE. Public for serialization purposes */
    public RemoveRecords(Record.Type type, Optional<RecordName> name, Optional<RecordData> data) {
        this.type = Objects.requireNonNull(type, "type must be non-null");
        this.name = Objects.requireNonNull(name, "name must be non-null");
        this.data = Objects.requireNonNull(data, "data must be non-null");
        if (name.isEmpty() && data.isEmpty()) {
            throw new IllegalArgumentException("at least one of name and data must be non-empty");
        }
    }

    public Record.Type type() {
        return type;
    }

    public Optional<RecordName> name() {
        return name;
    }

    public Optional<RecordData> data() {
        return data;
    }

    @Override
    public void dispatchTo(NameService nameService) {
        List<Record> records = new ArrayList<>();
        if (name.isPresent() && data.isPresent()) {
            nameService.findRecords(type, name.get())
                       .stream()
                       .filter(record -> {
                           // Records to remove must match both name and data fields
                           String dataValue = switch (record.type()) {
                               case ALIAS -> AliasTarget.unpack(record.data()).name().value();
                               case DIRECT -> DirectTarget.unpack(record.data()).recordData().asString();
                               default -> record.data().asString();
                           };
                           return fqdn(dataValue).equals(fqdn(data.get().asString()));
                       })
                       .forEach(records::add);
        } else {
            name.ifPresent(n -> records.addAll(nameService.findRecords(type, n)));
            data.ifPresent(d -> records.addAll(nameService.findRecords(type, d)));
        }
        nameService.removeRecords(records);
    }

    @Override
    public String toString() {
        return "remove records of type " + type + ", by " +
               name.map(n -> "name " + n).orElse("") +
               data.map(d -> "data " + d).orElse("");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RemoveRecords that = (RemoveRecords) o;
        return type == that.type &&
               name.equals(that.name) &&
               data.equals(that.data);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, name, data);
    }

    private static String fqdn(String name) {
        return name.endsWith(".") ? name : name + ".";
    }

}
