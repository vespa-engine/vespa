// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus.jdisc;

import com.yahoo.jdisc.Request;
import com.yahoo.jdisc.service.CurrentContainer;
import com.yahoo.messagebus.Message;

import java.net.URI;
import java.util.Objects;

/**
 * @author Simon Thoresen Hult
 */
public class MbusRequest extends Request {

    private final Message message;

    public MbusRequest(CurrentContainer current, URI uri, Message msg) {
        this(current, uri, msg, true);
    }
    public MbusRequest(CurrentContainer current, URI uri, Message msg, boolean isServerRequest) {
        super(current, validateParams(msg, uri), isServerRequest);
        this.message = msg;
    }

    public MbusRequest(Request parent, URI uri, Message msg) {
        super(parent, validateParams(msg, uri));
        this.message = msg;
    }

    public Message getMessage() {
        return message;
    }

    private static URI validateParams(Message msg, URI uri) {
        Objects.requireNonNull(msg, "msg cannot be null");
        return uri;
    }
}
