// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.log.event;

public class Histogram extends Event {
    public Histogram () {
    }

    public Histogram (String name, String value, String representation) {
        init(name, value, representation);
    }

    private void init (String name, String value, String representation) {
        setValue("name", name);
        setValue("counts", value);
        setValue("representation", representation);
    }
}
