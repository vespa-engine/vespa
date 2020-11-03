// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.api;

import java.time.Instant;
import java.util.Map;

/**
 * Status of reindexing for the documents of an application.
 *
 * @author jonmv
 */
public interface Reindexing {

    /** Reindexing status for a given application, cluster and document type. */
    default Status status(String cluster, String documentType) { return () -> Instant.MAX; }

    /** Reindexing status of a given document type in a given cluster in a given application. */
    interface Status {

        /** The instant at which reindexing may begin. */
        Instant ready();

    }

}
