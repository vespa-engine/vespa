// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus.test;

import com.yahoo.messagebus.Reply;
import com.yahoo.text.Utf8String;

/**
 * @author havardpe
 */
public class SimpleReply extends Reply {

    private String value;

    public SimpleReply(String value) {
        this.value = value;
    }

    public int getType() {
        return SimpleProtocol.REPLY;
    }

    public Utf8String getProtocol() {
        return SimpleProtocol.NAME;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}
