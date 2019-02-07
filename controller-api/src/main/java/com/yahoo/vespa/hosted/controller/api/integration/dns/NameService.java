// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
     * @param alias The alias to create
     * @param canonicalName The canonical name which the alias should point to. This must be a FQDN.
     */
    RecordId createCname(RecordName alias, RecordData canonicalName);

    /** Create a non-standard ALIAS record pointing to given targets. Implementations of this are expected to be idempotent */
    RecordId createAlias(RecordName name, Set<AliasTarget> targets);

    /** Find records matching type and name */
    List<Record> findRecords(Record.Type type, RecordName name);

    /** Find records matching type and data */
    List<Record> findRecords(Record.Type type, RecordData data);

    /** Update existing record */
    void updateRecord(RecordId id, RecordData newData);

    /** Remove record by ID */
    void removeRecord(RecordId id);

}
