// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.persistence;

import com.yahoo.component.Version;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.hosted.controller.versions.ControllerVersion;

import java.time.Instant;

/**
 * Serializer for {@link com.yahoo.vespa.hosted.controller.versions.ControllerVersion}.
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
        // TODO(mpolden): Make the following two fields non-optional after August 2019
        var commitSha = Serializers.optionalString(root.field(COMMIT_SHA_FIELD))
                                   .orElse("badc0ffee");
        var commitDate = Serializers.optionalInstant(root.field(COMMIT_DATE_FIELD))
                                    .orElse(Instant.EPOCH);
        return new ControllerVersion(version, commitSha, commitDate);
    }

}
