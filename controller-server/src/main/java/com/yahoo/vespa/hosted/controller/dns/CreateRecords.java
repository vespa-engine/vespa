// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.dns;

import com.yahoo.vespa.hosted.controller.api.integration.dns.AliasTarget;
import com.yahoo.vespa.hosted.controller.api.integration.dns.DirectTarget;
import com.yahoo.vespa.hosted.controller.api.integration.dns.NameService;
import com.yahoo.vespa.hosted.controller.api.integration.dns.Record;
import com.yahoo.vespa.hosted.controller.api.integration.dns.RecordName;
import com.yahoo.vespa.hosted.controller.application.TenantAndApplicationId;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Create or update multiple records of the same type and name.
 *
 * @author mpolden
 */
public class CreateRecords extends AbstractNameServiceRequest {

    private final Record.Type type;
    private final List<Record> records;

    /** DO NOT USE. Public for serialization purposes */
    public CreateRecords(Optional<TenantAndApplicationId> owner, List<Record> records) {
        super(owner, requireOneOf(Record::name, records));
        this.type = requireOneOf(Record::type, records);
        this.records = List.copyOf(Objects.requireNonNull(records, "records must be non-null"));
        if (type != Record.Type.ALIAS && type != Record.Type.TXT && type != Record.Type.DIRECT) {
            throw new IllegalArgumentException("Records of type " + type + " are not supported: " + records);
        }
    }

    public List<Record> records() {
        return records;
    }

    @Override
    public void dispatchTo(NameService nameService) {
        switch (type) {
            case ALIAS -> {
                var targets = records.stream().map(Record::data).map(AliasTarget::unpack).collect(Collectors.toSet());
                nameService.createAlias(name(), targets);
            }
            case DIRECT -> {
                var targets = records.stream().map(Record::data).map(DirectTarget::unpack).collect(Collectors.toSet());
                nameService.createDirect(name(), targets);
            }
            case TXT -> {
                var dataFields = records.stream().map(Record::data).toList();
                nameService.createTxtRecords(name(), dataFields);
            }
        }
    }

    @Override
    public String toString() {
        return "create records " + records();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CreateRecords that = (CreateRecords) o;
        return owner().equals(that.owner()) && records.equals(that.records);
    }

    @Override
    public int hashCode() {
        return Objects.hash(owner(), records);
    }

    /** Find exactly one distinct value of field in given list */
    private static <T, V> T requireOneOf(Function<V, T> field, List<V> list) {
        Set<T> values = list.stream().map(field).collect(Collectors.toSet());
        if (values.size() != 1) {
            throw new IllegalArgumentException("Expected one distinct value, but found " + values + " in " + list);
        }
        return values.iterator().next();
    }

}
