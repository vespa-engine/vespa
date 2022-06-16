// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.server;

/**
 * Return types for the server.
 *
 * @author Einar M R Rosenvinge
 * @author Steinar Knutsen
 */
enum ErrorCode {

    OK(true, true),
    ERROR(false, false),
    TRANSIENT_ERROR(false, true),
    END_OF_FEED(true, true);

    private final boolean success;
    private final boolean _transient;

    ErrorCode(boolean success, boolean _transient) {
        this.success = success;
        this._transient = _transient;
    }

    public boolean isSuccess() {
        return success;
    }

    public boolean isTransient() {
        return _transient;
    }

}
