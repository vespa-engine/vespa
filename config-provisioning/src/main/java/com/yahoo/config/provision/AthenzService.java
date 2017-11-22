// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

/**
 * @author mortent
 */
public class AthenzService {

    private final String name;

    private AthenzService(String name) {
        this.name = name;
    }

    public String value() { return name; }

    public static AthenzService from(String value) {
        return new AthenzService(value);
    }
}
