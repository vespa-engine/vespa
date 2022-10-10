// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.dns;

import java.util.List;
import java.util.Set;

/**
 * A managed DNS service.
 *
 * @author mpolden
 */
public interface NameService {

    /**
     * Create a new record
     *
     * @param type The DNS type of record to make, only a small set of types are supported, check with the implementation
     * @param name Name of the record, e.g. a FQDN for records of type A
     * @param data Data of the record, e.g. IP address for records of type A
     * @return The created record
     */
    Record createRecord(Record.Type type, RecordName name, RecordData data);

    /**
     * Create a non-standard ALIAS record pointing to given targets. Implementations of this are expected to be
     * idempotent
     *
     * @param targets Targets that should be resolved by this name.
     * @return The created records. One per target.
     */
    List<Record> createAlias(RecordName name, Set<AliasTarget> targets);

    /**
     * Create a non-standard record pointing to given targets. Implementations of this are expected to be
     * idempotent
     *
     * @param targets Targets that should be resolved by this name.
     * @return The created records. One per target.
     */
    List<Record> createDirect(RecordName name, Set<DirectTarget> targets);

    /**
     * Create a new TXT record containing the provided data.
     * @param name Name of the created record
     * @param txtRecords TXT data values for the record, each consisting of one or more space-separated double-quoted
     *                   strings: "string1" "string2"
     * @return The created records
     */
    List<Record> createTxtRecords(RecordName name, List<RecordData> txtRecords);

    /** Find all records matching given type and name */
    List<Record> findRecords(Record.Type type, RecordName name);

    /** Find all records matching given type and data */
    List<Record> findRecords(Record.Type type, RecordData data);

    /** Update existing record */
    void updateRecord(Record record, RecordData newData);

    /** Remove given record(s) */
    void removeRecords(List<Record> record);

}
