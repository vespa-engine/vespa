// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.log.event;

public class CountGroup extends Event {
    public CountGroup () {
    }

    public CountGroup (String name, String values) {
        init(name, values);
    }

    private void init (String name, String counts) {
        setValue("name", name);
        setValue("values", counts);
    }
}
