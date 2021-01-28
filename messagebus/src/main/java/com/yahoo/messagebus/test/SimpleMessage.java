// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus.test;

import com.yahoo.messagebus.Message;
import com.yahoo.text.Utf8String;

/**
 * @author havardpe
 */
public class SimpleMessage extends Message {

    private String value;

    public SimpleMessage(String value) {
        this.value = value;
    }

    @Override
    public int getType() {
        return SimpleProtocol.MESSAGE;
    }

    @Override
    public Utf8String getProtocol() {
        return SimpleProtocol.NAME;
    }

    @Override
    public int getApproxSize() {
        return value.length();
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}
