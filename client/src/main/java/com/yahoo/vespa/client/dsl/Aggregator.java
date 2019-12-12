// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.client.dsl;

public class Aggregator {

    String type;
    Object value = "";

    Aggregator(String type) {
        this.type = type;
    }

    Aggregator(String type, Object value) {
        this.type = type;
        this.value = value;
    }

    @Override
    public String toString() {
        return String.format("%s(%s)", type, value);
    }
}
