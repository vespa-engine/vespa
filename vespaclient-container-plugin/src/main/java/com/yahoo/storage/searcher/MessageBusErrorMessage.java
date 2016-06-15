// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.storage.searcher;

import com.yahoo.search.result.ErrorMessage;

/**
 * Simple ErrorMessage extension that includes a message bus error code, not
 * just the searcher error code (which isn't very useful for a Vespa XML consumer)
 */
public class MessageBusErrorMessage extends ErrorMessage {

    private int mbusCode;

    public MessageBusErrorMessage(int mbusCode, int qrsCode, String message) {
        super(qrsCode, message);
        this.mbusCode = mbusCode;
    }

    public MessageBusErrorMessage(int mbusCode, int qrsCode, String message, String detailedMessage) {
        super(qrsCode, message, detailedMessage);
        this.mbusCode = mbusCode;
    }

    public MessageBusErrorMessage(int mbusCode, int qrsCode, String message, String detailedMessage, Throwable cause) {
        super(qrsCode, message, detailedMessage, cause);
        this.mbusCode = mbusCode;
    }

    public int getMessageBusCode() {
        return mbusCode;
    }

    public void setMessageBusCode(int code) {
        this.mbusCode = code;
    }

}
