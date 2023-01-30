// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.dns;

import com.yahoo.vespa.hosted.controller.api.integration.dns.AliasTarget;
import com.yahoo.vespa.hosted.controller.api.integration.dns.DirectTarget;
import com.yahoo.vespa.hosted.controller.api.integration.dns.NameService;
import com.yahoo.vespa.hosted.controller.api.integration.dns.Record;
import com.yahoo.vespa.hosted.controller.api.integration.dns.RecordData;
import com.yahoo.vespa.hosted.controller.api.integration.dns.RecordName;
import com.yahoo.vespa.hosted.controller.application.TenantAndApplicationId;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Permanently removes all matching records by type and matching either:
 *
 * - name and data
 * - only name
 *
 * @author mpolden
 */
public class RemoveRecords extends AbstractNameServiceRequest {

    private final Record.Type type;
    private final Optional<RecordData> data;

    public RemoveRecords(Optional<TenantAndApplicationId> owner, Record.Type type, RecordName name) {
        this(owner, type, name, Optional.empty());
    }

    public RemoveRecords(Optional<TenantAndApplicationId> owner, Record.Type type, RecordName name, RecordData data) {
        this(owner, type, name, Optional.of(data));
    }

    /** DO NOT USE. Public for serialization purposes */
    public RemoveRecords(Optional<TenantAndApplicationId> owner, Record.Type type, RecordName name, Optional<RecordData> data) {
        super(owner, name);
        this.type = Objects.requireNonNull(type, "type must be non-null");
        this.data = Objects.requireNonNull(data, "data must be non-null");
    }

    public Record.Type type() {
        return type;
    }

    public Optional<RecordData> data() {
        return data;
    }

    @Override
    public void dispatchTo(NameService nameService) {
        // Deletions require all records fields to match exactly, data may be incomplete even if present. To ensure
        // completeness we search for the record(s) first
        List<Record> completeRecords = nameService.findRecords(type, name()).stream()
                                                  .filter(record -> data.isEmpty() || matchingFqdnIn(data.get(), record))
                                                  .toList();
        nameService.removeRecords(completeRecords);
    }

    @Override
    public String toString() {
        return "remove records of type " + type + ", by name " + name() +
               data.map(d -> " data " + d).orElse("");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RemoveRecords that = (RemoveRecords) o;
        return owner().equals(that.owner()) && type == that.type && name().equals(that.name()) && data.equals(that.data);
    }

    @Override
    public int hashCode() {
        return Objects.hash(owner(), type, name(), data);
    }

    private static boolean matchingFqdnIn(RecordData data, Record record) {
        String dataValue = switch (record.type()) {
            case ALIAS -> AliasTarget.unpack(record.data()).name().value();
            case DIRECT -> DirectTarget.unpack(record.data()).recordData().asString();
            default -> record.data().asString();
        };
        return fqdn(dataValue).equals(fqdn(data.asString()));
    }

    private static String fqdn(String name) {
        return name.endsWith(".") ? name : name + ".";
    }

}
