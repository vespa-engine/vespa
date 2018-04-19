// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.persistence;

import com.yahoo.component.Version;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.Slime;

/**
 * Serializer for version numbers.
 *
 * @author mpolden
 */
public class VersionSerializer {

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
