// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.server;

import com.yahoo.messagebus.Error;

import java.util.Collection;
import java.util.Set;

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

    private static final Collection<Integer> MBUS_FATALS_HANDLED_AS_TRANSIENT = Set.of(
            com.yahoo.messagebus.ErrorCode.SEND_QUEUE_CLOSED,
            com.yahoo.messagebus.ErrorCode.ILLEGAL_ROUTE,
            com.yahoo.messagebus.ErrorCode.NO_SERVICES_FOR_ROUTE,
            com.yahoo.messagebus.ErrorCode.NETWORK_ERROR,
            com.yahoo.messagebus.ErrorCode.SEQUENCE_ERROR,
            com.yahoo.messagebus.ErrorCode.NETWORK_SHUTDOWN,
            com.yahoo.messagebus.ErrorCode.TIMEOUT);

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

    static ErrorCode fromBusError(Error mbusError) {
        return mbusError.isFatal() && !MBUS_FATALS_HANDLED_AS_TRANSIENT.contains(mbusError.getCode())
                ? ERROR : TRANSIENT_ERROR;
    }

}
