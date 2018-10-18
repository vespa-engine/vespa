// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.federation.http;

/**
 * Timeout marker for slow HTTP connections.
 *
 * @author <a href="mailto:steinar@yahoo-inc.com">Steinar Knutsen</a>
 * @deprecated
 */
// TODO: Remove on Vespa 7
@Deprecated // OK
public class TimeoutException extends RuntimeException {

    /**
     * Auto-generated version ID.
     */
    private static final long serialVersionUID = 7084147598258586559L;

    public TimeoutException(String message) {
        super(message);
    }

}
