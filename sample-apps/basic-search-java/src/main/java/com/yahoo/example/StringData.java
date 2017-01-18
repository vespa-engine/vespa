// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.example;

import com.yahoo.processing.Request;
import com.yahoo.processing.response.AbstractData;

public class StringData extends AbstractData {

    private final String string;

    public StringData(Request request, String string) {
        super(request);
        this.string = string;
    }

    @Override
    public String toString() {
        return string;
    }
}