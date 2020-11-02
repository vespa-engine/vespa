// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.api;

import java.time.Instant;
import java.util.Map;

/**
 * Instants after which reindexing should be triggered, for select document types.
 *
 * @author jonmv
 */
public interface Reindexing {

    /** The reindexing status for each document type for which this is known. */
    default Map<String, ? extends Status> status() { return Map.of(); }


    /** Reindexing status of a given document type. */
    interface Status {

        /** The instant at which reindexing of this document type may begin. */
        default Instant ready() { return Instant.MAX; };

    }

}
