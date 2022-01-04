// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.versions;

import com.yahoo.component.Version;

import java.util.Objects;

/**
 * The target Vespa version for a system.
 *
 * @author mpolden
 */
public class VespaVersionTarget implements VersionTarget {

    private final Version version;
    private final boolean downgrade;

    public VespaVersionTarget(Version version, boolean downgrade) {
        this.version = Objects.requireNonNull(version);
        this.downgrade = downgrade;
    }

    @Override
    public Version version() {
        return version;
    }

    @Override
    public boolean downgrade() {
        return downgrade;
    }

}
