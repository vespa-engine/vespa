// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.persistence;

import com.yahoo.component.Version;
import com.yahoo.config.provision.NodeType;
import com.yahoo.slime.ObjectTraverser;
import com.yahoo.slime.Slime;
import com.yahoo.slime.Type;
import com.yahoo.vespa.config.SlimeUtils;
import com.yahoo.vespa.hosted.provision.os.OsVersion;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.TreeMap;

/**
 * Serializer for a map of {@link NodeType} and {@link OsVersion}.
 *
 * @author mpolden
 */
public class OsVersionsSerializer {

    private static final String VERSION_FIELD = "version";
    private static final String ACTIVE_FIELD = "active";

    private OsVersionsSerializer() {}

    public static byte[] toJson(Map<NodeType, OsVersion> versions) {
        var slime = new Slime();
        var object = slime.setObject();
        versions.forEach((nodeType, osVersion) -> {
            var versionObject = object.setObject(NodeSerializer.toString(nodeType));
            versionObject.setString(VERSION_FIELD, osVersion.version().toFullString());
            versionObject.setBool(ACTIVE_FIELD, osVersion.active());
        });
        try {
            return SlimeUtils.toJsonBytes(slime);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static Map<NodeType, OsVersion> fromJson(byte[] data) {
        var versions = new TreeMap<NodeType, OsVersion>(); // Use TreeMap to sort by node type
        var inspector = SlimeUtils.jsonToSlime(data).get();
        inspector.traverse((ObjectTraverser) (key, value) -> {
            Version version;
            boolean active;
            if (value.type() == Type.OBJECT) {
                version = Version.fromString(value.field(VERSION_FIELD).asString());
                active = value.field(ACTIVE_FIELD).asBool();
            } else {
                // TODO(mpolden): Remove support for legacy format after September 2019
                version = Version.fromString(value.asString());
                active = true;
            }
            versions.put(NodeSerializer.nodeTypeFromString(key), new OsVersion(version, active));
        });
        return versions;
    }

}
