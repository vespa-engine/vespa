// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.dns;

import java.util.Optional;

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
     * @param canonicalName The canonical name which the alias should point to. This must be a domain.
     */
    RecordId createCname(String alias, String canonicalName);

    /** Find record by type and name */
    Optional<Record> findRecord(Record.Type type, String name);

}
