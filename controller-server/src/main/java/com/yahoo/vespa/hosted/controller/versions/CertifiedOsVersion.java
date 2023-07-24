package com.yahoo.vespa.hosted.controller.versions;

import com.yahoo.component.Version;

import java.util.Objects;

/**
 * An OS version that has been certified to work on a specific Vespa version.
 *
 * @author mpolden
 */
public record CertifiedOsVersion(OsVersion osVersion, Version vespaVersion) {

    public CertifiedOsVersion {
        Objects.requireNonNull(osVersion);
        Objects.requireNonNull(vespaVersion);
    }

}
