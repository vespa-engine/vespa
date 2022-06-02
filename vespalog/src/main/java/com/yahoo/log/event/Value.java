// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.log.event;

/**
 *
 * @author  Bjorn Borud
 */
class Value extends Event {
    public Value () {
    }

    public Value (String name, double value) {
        setValue("name", name);
        setValue("value", Double.toString(value));
    }
}
