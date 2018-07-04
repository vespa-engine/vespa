// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.messagebus.systemstate.rule;

/**
 * @author Simon Thoresen Hult
 */
public class Argument {

    private final String name;
    private final String value;

    /**
     * Constructs a new argument.
     *
     * @param name  The name of this argument.
     * @param value The value of this argument.
     */
    public Argument(String name, String value) {
        this.name = name;
        this.value = value;
    }

    /**
     * Returns the name of this argument.
     *
     * @return The name.
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the value of this argument.
     *
     * @return The value.
     */
    public String getValue() {
        return value;
    }
}
