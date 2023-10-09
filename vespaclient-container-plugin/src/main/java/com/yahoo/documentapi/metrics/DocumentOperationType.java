// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.metrics;

import com.yahoo.documentapi.messagebus.protocol.PutDocumentMessage;
import com.yahoo.documentapi.messagebus.protocol.RemoveDocumentMessage;
import com.yahoo.documentapi.messagebus.protocol.UpdateDocumentMessage;
import com.yahoo.messagebus.Message;

/**
 * @author freva
 */
public enum DocumentOperationType {

    PUT, REMOVE, UPDATE, ERROR;

    public static DocumentOperationType fromMessage(Message msg) {
        if (msg instanceof PutDocumentMessage) {
            return PUT;
        } else if (msg instanceof RemoveDocumentMessage) {
            return REMOVE;
        } else if (msg instanceof UpdateDocumentMessage) {
            return UPDATE;
        } else {
            return ERROR;
        }
    }

}

