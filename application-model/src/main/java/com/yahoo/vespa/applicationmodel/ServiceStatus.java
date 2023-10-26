// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.applicationmodel;

/**
 * @author oyving
 */
public enum ServiceStatus {
    UP,
    DOWN,

    /** The status has not yet been probed or has expired.  A status of UP or DOWN is expected shortly. */
    UNKNOWN,

    /** The service is not monitored for health, and will never get any other status than NOT_CHECKED. */
    NOT_CHECKED
}
