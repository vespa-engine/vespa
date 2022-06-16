// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.versions;

import com.yahoo.component.Version;

import java.util.Objects;

/**
 * The target Vespa version for a system.
 *
 * @author mpolden
 */
public record VespaVersionTarget(Version version, boolean downgrade) implements VersionTarget {

    public VespaVersionTarget {
        Objects.requireNonNull(version);
    }

    @Override
    public String toString() {
        return "vespa version target " + version.toFullString() + (downgrade ? " (downgrade)" : "");
    }

}
