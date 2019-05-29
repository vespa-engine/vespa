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

    /** Continuous deployment system */
    cd(SystemType.MAIN, true),

    /** Production system */
    main(SystemType.MAIN, false),

    /** System accessible to the public */
    Public(SystemType.PUBLIC, false),

    /** Continuous deployment system for testing the Public system */
    PublicCd(SystemType.PUBLIC, true),

    /** Local development system */
    dev(SystemType.MAIN, false),

    /** VaaS */
    vaas(SystemType.PUBLIC, true); // TODO: Remove this and use public everywhere

    private final SystemType type;
    private final boolean isCd;

    SystemName(SystemType type, boolean isCd) {
        this.type = type;
        this.isCd = isCd;
    }

    public static SystemName defaultSystem() {
        return main;
    }

    public static SystemName from(String value) {
        switch (value.toLowerCase()) {
            case "dev": return dev;
            case "cd": return cd;
            case "main": return main;
            case "public": return Public;
            case "publiccd": return PublicCd;
            case "vaas": return vaas;
            default: throw new IllegalArgumentException(String.format("'%s' is not a valid system", value));
        }
    }

    public String value() {
        switch (this) {
            case dev: return "dev";
            case cd: return "cd";
            case main: return "main";
            case Public: return "public";
            case PublicCd: return "publiccd";
            case vaas: return "vaas";
            default : throw new IllegalStateException();
        }
    }

    public SystemType getType() { return type; }
    public boolean isCd() { return isCd; }
    public static Set<SystemName> all() { return EnumSet.allOf(SystemName.class); }
}
