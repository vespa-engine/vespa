// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus.jdisc;

import com.yahoo.jdisc.Request;
import com.yahoo.jdisc.service.CurrentContainer;
import com.yahoo.messagebus.Message;

import java.net.URI;

/**
 * @author Simon Thoresen Hult
 */
public class MbusRequest extends Request {

    private final Message message;

    public MbusRequest(CurrentContainer current, URI uri, Message msg) {
        this(current, uri, msg, true);
    }
    public MbusRequest(CurrentContainer current, URI uri, Message msg, boolean isServerRequest) {
        super(current, uri, isServerRequest);
        this.message = validateMessage(msg);
    }

    public MbusRequest(Request parent, URI uri, Message msg) {
        super(parent, uri);
        this.message = validateMessage(msg);
    }

    public Message getMessage() {
        return message;
    }

    private Message validateMessage(Message msg) {
        if (msg != null) {
            return msg;
        }
        release();
        throw new NullPointerException();
    }
}
