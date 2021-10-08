// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.persistence;

import com.yahoo.component.Version;
import com.yahoo.config.provision.CloudName;
import com.yahoo.slime.ArrayTraverser;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.hosted.controller.versions.OsVersion;

import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

/**
 * Serializer for an {@link OsVersion}.
 *
 * @author mpolden
 */
public class OsVersionSerializer {

    // WARNING: Since there are multiple servers in a ZooKeeper cluster and they upgrade one by one
    //          (and rewrite all nodes on startup), changes to the serialized format must be made
    //          such that what is serialized on version N+1 can be read by version N:
    //          - ADDING FIELDS: Always ok
    //          - REMOVING FIELDS: Stop reading the field first. Stop writing it on a later version.
    //          - CHANGING THE FORMAT OF A FIELD: Don't do it bro.

    private static final String versionsField = "versions";
    private static final String versionField = "version";
    private static final String cloudField = "cloud";

    public Slime toSlime(Set<OsVersion> osVersions) {
        Slime slime = new Slime();
        Cursor root = slime.setObject();
        Cursor array = root.setArray(versionsField);
        osVersions.forEach(osVersion -> toSlime(osVersion, array.addObject()));
        return slime;
    }

    public void toSlime(OsVersion osVersion, Cursor object) {
        object.setString(versionField, osVersion.version().toFullString());
        object.setString(cloudField, osVersion.cloud().value());
    }

    public Set<OsVersion> fromSlime(Slime slime) {
        Inspector array = slime.get().field(versionsField);
        Set<OsVersion> osVersions = new TreeSet<>();
        array.traverse((ArrayTraverser) (i, inspector) -> osVersions.add(fromSlime(inspector)));
        return Collections.unmodifiableSet(osVersions);
    }

    public OsVersion fromSlime(Inspector object) {
        return new OsVersion(
                Version.fromString(object.field(versionField).asString()),
                CloudName.from(object.field(cloudField).asString())
        );
    }

}
