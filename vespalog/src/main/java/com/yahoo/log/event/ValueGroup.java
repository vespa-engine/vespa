// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.log.event;

public class ValueGroup extends Event {
    public ValueGroup () {
    }

    public ValueGroup (String name, String values) {
        init(name, values);
    }

    private void init (String name, String value) {
        setValue("name", name);
        setValue("values", value);
    }
}
