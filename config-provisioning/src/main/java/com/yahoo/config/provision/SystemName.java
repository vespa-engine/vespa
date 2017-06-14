// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

/**
 * Systems in hosted Vespa
 *
 * @author mpolden
 */
public enum SystemName {

    /** System for continuous deployment where a pre-test of hosted Vespa combined with a verified Vespa version */
    cd,

    /** Production system */
    main;

    public static SystemName defaultSystem() {
        return main;
    }

    public static SystemName from(String value) {
        switch (value) {
            case "cd": return cd;
            case "main": return main;
            default: throw new IllegalArgumentException(String.format("'%s' is not a valid system", value));
        }
    }

}
