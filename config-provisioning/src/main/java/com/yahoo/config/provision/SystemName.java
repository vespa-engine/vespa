// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

/**
 * Systems in hosted Vespa
 *
 * @author mpolden
 */
public enum SystemName {

    /** Local development system */
    dev,

    /** Continuous deployment system */
    cd,

    /** Production system */
    main,

    /** Vespa as a service */
    vaas;

    public static SystemName defaultSystem() {
        return main;
    }

    public static SystemName from(String value) {
        switch (value) {
            case "dev": return dev;
            case "cd": return cd;
            case "main": return main;
            case "vaas": return vaas;
            default: throw new IllegalArgumentException(String.format("'%s' is not a valid system", value));
        }
    }

}
