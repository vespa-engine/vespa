// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.persistence;

import com.yahoo.component.Version;
import com.yahoo.config.provision.CloudName;
import com.yahoo.slime.ArrayTraverser;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.hosted.controller.versions.CertifiedOsVersion;
import com.yahoo.vespa.hosted.controller.versions.OsVersion;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Serializer for {@link com.yahoo.vespa.hosted.controller.versions.CertifiedOsVersion}.
 *
 * @author mpolden
 */
public class CertifiedOsVersionSerializer {

    private static final String versionField = "version";
    private static final String cloudField = "cloud";
    private static final String vespaVersionField = "vespaVersion";

    public Slime toSlime(Set<CertifiedOsVersion> versions) {
        Slime slime = new Slime();
        Cursor array = slime.setArray();
        for (var version : versions) {
            Cursor root = array.addObject();
            root.setString(versionField, version.osVersion().version().toFullString());
            root.setString(cloudField, version.osVersion().cloud().value());
            root.setString(vespaVersionField, version.vespaVersion().toFullString());
        }
        return slime;
    }

    public Set<CertifiedOsVersion> fromSlime(Slime slime) {
        Cursor array = slime.get();
        Set<CertifiedOsVersion> certifiedOsVersions = new HashSet<>();
        array.traverse((ArrayTraverser) (idx, object) -> certifiedOsVersions.add(
                new CertifiedOsVersion(new OsVersion(Version.fromString(object.field(versionField).asString()),
                                                     CloudName.from(object.field(cloudField).asString())),
                                       Version.fromString(object.field(vespaVersionField).asString())))
        );
        return Collections.unmodifiableSet(certifiedOsVersions);
    }

}
