// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.persistence;

import com.yahoo.component.Version;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.Slime;

/**
 * Serializer for {@link Version}.
 *
 * @author mpolden
 */
public class VersionSerializer {

    // WARNING: Since there are multiple servers in a ZooKeeper cluster and they upgrade one by one
    //          (and rewrite all nodes on startup), changes to the serialized format must be made
    //          such that what is serialized on version N+1 can be read by version N:
    //          - ADDING FIELDS: Always ok
    //          - REMOVING FIELDS: Stop reading the field first. Stop writing it on a later version.
    //          - CHANGING THE FORMAT OF A FIELD: Don't do it bro.

    private static final String versionField = "version";

    public Slime toSlime(Version version) {
        Slime slime = new Slime();
        Cursor root = slime.setObject();
        root.setString(versionField, version.toFullString());
        return slime;
    }

    public Version fromSlime(Slime slime) {
        Inspector root = slime.get();
        return Version.fromString(root.field(versionField).asString());
    }

}
