// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.metrics;

import com.yahoo.documentapi.messagebus.protocol.DocumentProtocol;

import java.util.Set;

/**
 * Enum with possible outcomes of a single document feeding operation:
 * <ul>
 * <li>OK: Document was successfully added/updated/removed</li>
 * <li>REQUEST_ERROR: User-made error, for example invalid document format</li>
 * <li>SERVER_ERROR: Server-made error, for example insufficient disk space</li>
 * </ul>
 *
 * @author freva
 */
public enum DocumentOperationStatus {

    OK, REQUEST_ERROR, SERVER_ERROR;

    public static DocumentOperationStatus fromMessageBusErrorCodes(Set<Integer> errorCodes) {
        if (errorCodes.size() == 1 && errorCodes.contains(DocumentProtocol.ERROR_NO_SPACE))
            return SERVER_ERROR;

        return REQUEST_ERROR;
    }

}
