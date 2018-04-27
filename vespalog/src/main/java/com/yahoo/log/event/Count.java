// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.log.event;

/**
 *
 * @author  Bjorn Borud
 */
public class Count extends Event {
    public Count () {
    }

    public Count (String name, double value) {
        setValue("name", name);
        setValue("value", Double.toString(value));
    }

    /**
     * Set a property.
     *
     * @param name The name of the property
     * @param value The value of the property
     */
    @Override
    public Event setValue (String name, String value) {
        if (name.equals("value")) {
            super.setValue(name, Long.toString((Double.valueOf(value)).longValue()));
        } else {
            super.setValue(name , value);
        }
        return this;
    }
}
