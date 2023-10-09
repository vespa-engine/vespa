// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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

    Mode(ModeName modeName) {
        mode = modeName;
    }

    Mode(String modeString) {
        switch (modeString.toLowerCase()) {
            case "default" :
                mode = ModeName.DEFAULT;
                break;
            case "memorycache" :
                mode = ModeName.MEMORYCACHE;
                break;
            default:
                throw new IllegalArgumentException("Unrecognized mode '" + modeString + "' supplied. Legal modes are '" + Mode.modes() + "'");
        }
    }

    ModeName getMode() {
        return mode;
    }

    boolean isDefault() {
        return mode.equals(ModeName.DEFAULT);
    }

    boolean requiresConfigSource() {
        return mode.equals(ModeName.DEFAULT);
    }

    static Set<String> modes() {
        Set<String> modes = new HashSet<>();
        for (ModeName mode : ModeName.values()) {
            modes.add(mode.name().toLowerCase());
        }
        return modes;
    }

    String name() {
        return mode.name().toLowerCase();
    }

    @Override
    public String toString() {
        return mode.name().toLowerCase();
    }
}
