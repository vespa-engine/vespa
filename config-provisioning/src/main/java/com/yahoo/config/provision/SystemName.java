// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import java.util.EnumSet;
import java.util.Set;

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

    /** System accessible for the public */
    Public,

    /** VaaS */
    vaas;

    public static SystemName defaultSystem() {
        return main;
    }

    public static SystemName from(String value) {
        switch (value) {
            case "dev": return dev;
            case "cd": return cd;
            case "main": return main;
            case "public": return Public;
            case "Public": return Public;
            case "vaas": return vaas;
            default: throw new IllegalArgumentException(String.format("'%s' is not a valid system", value));
        }
    }

    public static Set<SystemName> all() {
        return EnumSet.allOf(SystemName.class);
    }

}
