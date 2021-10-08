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
     * Create a new CNAME record
     *
     * @param name          The alias to create (lhs of the record)
     * @param canonicalName The canonical name which the alias should point to (rhs of the record). This must be a FQDN.
     * @return The created record
     */
    Record createCname(RecordName name, RecordData canonicalName);

    /**
     * Create a non-standard ALIAS record pointing to given targets. Implementations of this are expected to be
     * idempotent
     *
     * @param targets Targets that should be resolved by this name.
     * @return The created records. One per target.
     */
    List<Record> createAlias(RecordName name, Set<AliasTarget> targets);

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
