// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http;

/**
 * Exception indicating that the reindexing status for an application is currently unavailable, e.g. if the cluster is
 * recently configured and its nodes are not yet up.
 *
 * @author mpolden
 */
public class ReindexingStatusException extends RuntimeException {

    public ReindexingStatusException(String message) {
        super(message);
    }

}
