// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespastat;

/**
 * Exception class used by {@link com.yahoo.vespastat.BucketStatsRetriever}.
 *
 * @author bjorncs
 */
public class BucketStatsException extends Exception {
    public BucketStatsException(String message) {
        super(message);
    }

    public BucketStatsException(String message, Throwable cause) {
        super(message, cause);
    }

}
