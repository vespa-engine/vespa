// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.proxy;

import java.util.HashSet;
import java.util.Set;

/**
 *
 * The mode the config proxy can be running with.
 *
 * 'default' mode is requesting config from server, serving from cache only when known config
 * and no new config having been sent from server. When in 'memorycache' mode, there is no connection
 * to a config source, the proxy serves from memory cache only.
 *
 * @author hmusum
 */
class Mode {
    private final ModeName mode;

    enum ModeName {
        DEFAULT, MEMORYCACHE
    }

    public Mode() {
        this(ModeName.DEFAULT);
    }

    public Mode(ModeName modeName) {
        mode = modeName;
    }

    public Mode(String modeString) {
        switch (modeString.toLowerCase()) {
            case "" :
                mode = ModeName.DEFAULT;
                break;
            case "default" :
                mode = ModeName.DEFAULT;
                break;
            case "memorycache" :
                mode = ModeName.MEMORYCACHE;
                break;
            default:
                throw new IllegalArgumentException("Unrecognized mode'" + modeString + "' supplied");
        }
    }

    public ModeName getMode() {
        return mode;
    }

    public boolean isDefault() {
        return mode.equals(ModeName.DEFAULT);
    }

    public boolean isMemoryCache() {
        return mode.equals(ModeName.MEMORYCACHE);
    }

    public boolean requiresConfigSource() {
        return mode.equals(ModeName.DEFAULT);
    }

    public static boolean validModeName(String modeString) {
        return (modeString != null) && modes().contains(modeString);
    }

    static Set<String> modes() {
        Set<String> modes = new HashSet<>();
        for (ModeName mode : ModeName.values()) {
            modes.add(mode.name().toLowerCase());
        }
        return modes;
    }

    public String name() {
        return mode.name().toLowerCase();
    }

    @Override
    public String toString() {
        return mode.name().toLowerCase();
    }
}
