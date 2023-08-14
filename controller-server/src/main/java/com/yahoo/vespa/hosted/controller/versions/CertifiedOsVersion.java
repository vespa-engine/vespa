package com.yahoo.vespa.hosted.controller.versions;

import com.yahoo.component.Version;

import java.util.Comparator;
import java.util.Objects;

/**
 * An OS version that has been certified to work on a specific Vespa version.
 *
 * @author mpolden
 */
public record CertifiedOsVersion(OsVersion osVersion, Version vespaVersion) implements Comparable<CertifiedOsVersion> {

    private static final Comparator<CertifiedOsVersion> comparator = Comparator.comparing(CertifiedOsVersion::osVersion)
                                                                               .thenComparing(CertifiedOsVersion::vespaVersion);

    public CertifiedOsVersion {
        Objects.requireNonNull(osVersion);
        Objects.requireNonNull(vespaVersion);
    }

    @Override
    public int compareTo(CertifiedOsVersion that) {
        return comparator.compare(this, that);
    }

}
