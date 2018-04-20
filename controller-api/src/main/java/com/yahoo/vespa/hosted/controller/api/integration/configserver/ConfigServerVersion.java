// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.configserver;

import com.yahoo.component.Version;

/**
 * Represents the config server's current and wanted version.
 *
 * @author mpolden
 */
public class ConfigServerVersion {

    private final Version current;
    private final Version wanted;

    public ConfigServerVersion(Version current, Version wanted) {
        this.current = current;
        this.wanted = wanted;
    }

    public Version current() {
        return current;
    }

    public Version wanted() {
        return wanted;
    }

    public boolean upgrading() {
        return !current.equals(wanted);
    }
}
