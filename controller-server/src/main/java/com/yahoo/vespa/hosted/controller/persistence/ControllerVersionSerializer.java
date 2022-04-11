// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.persistence;

import com.yahoo.component.Version;
import com.yahoo.slime.Slime;
import com.yahoo.slime.SlimeUtils;
import com.yahoo.vespa.hosted.controller.api.identifiers.ControllerVersion;

/**
 * Serializer for {@link ControllerVersion}.
 *
 * @author mpolden
 */
public class ControllerVersionSerializer {

    // WARNING: Since there are multiple servers in a ZooKeeper cluster and they upgrade one by one
    //          (and rewrite all nodes on startup), changes to the serialized format must be made
    //          such that what is serialized on version N+1 can be read by version N:
    //          - ADDING FIELDS: Always ok
    //          - REMOVING FIELDS: Stop reading the field first. Stop writing it on a later version.
    //          - CHANGING THE FORMAT OF A FIELD: Don't do it bro.

    private static final String VERSION_FIELD = "version";
    private static final String COMMIT_SHA_FIELD = "commitSha";
    private static final String COMMIT_DATE_FIELD = "commitDate";

    public Slime toSlime(ControllerVersion controllerVersion) {
        var slime = new Slime();
        var root = slime.setObject();
        root.setString(VERSION_FIELD, controllerVersion.version().toFullString());
        root.setString(COMMIT_SHA_FIELD, controllerVersion.commitSha());
        root.setLong(COMMIT_DATE_FIELD, controllerVersion.commitDate().toEpochMilli());
        return slime;
    }

    public ControllerVersion fromSlime(Slime slime) {
        var root = slime.get();
        var version = Version.fromString(root.field(VERSION_FIELD).asString());
        var commitSha = root.field(COMMIT_SHA_FIELD).asString();
        var commitDate = SlimeUtils.instant(root.field(COMMIT_DATE_FIELD));
        return new ControllerVersion(version, commitSha, commitDate);
    }

}
