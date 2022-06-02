// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.log.event;

class State extends Event {
    public State () {
    }

    public State (String name, String value) {
        init(name, value);
    }

    private void init (String name, String value) {
        setValue("name", name);
        setValue("value", value);
    }
}
