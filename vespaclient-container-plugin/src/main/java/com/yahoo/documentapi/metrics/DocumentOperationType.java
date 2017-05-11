// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.metrics;

import com.yahoo.documentapi.messagebus.protocol.DocumentProtocol;
import com.yahoo.messagebus.Message;

/**
 * @author freva
 */
public enum DocumentOperationType {
    PUT, REMOVE, UPDATE;

    public static DocumentOperationType fromMessage(Message msg) {
        switch (msg.getType()) {
            case DocumentProtocol.MESSAGE_PUTDOCUMENT:
                return PUT;
            case DocumentProtocol.MESSAGE_UPDATEDOCUMENT:
                return UPDATE;
            case DocumentProtocol.MESSAGE_REMOVEDOCUMENT:
                return REMOVE;
            default:
                return null;
        }
    }
}

