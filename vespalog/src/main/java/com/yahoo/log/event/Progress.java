// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.log.event;

/**
 *
 * @author  Bjorn Borud
 */
class Progress extends Event {
    public Progress () {
    }

    public Progress (String name, String value, String total) {
        init(name, value, total);
    }

    public Progress (String name, double value, double total) {
        init(name, Double.toString(value), Double.toString(total));
    }

    public Progress (String name, String value) {
        init(name, value, "");
    }

    public Progress (String name, double value) {
        init(name, Double.toString(value), "");
    }

    private void init (String name, String value, String total) {
        setValue("name", name);
        setValue("value", value);
        setValue("total", total);
    }
}
