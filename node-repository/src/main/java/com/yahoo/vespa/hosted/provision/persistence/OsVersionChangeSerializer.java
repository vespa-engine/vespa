// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.persistence;

import com.yahoo.component.Version;
import com.yahoo.config.provision.NodeType;
import com.yahoo.slime.ArrayTraverser;
import com.yahoo.slime.ObjectTraverser;
import com.yahoo.slime.Slime;
import com.yahoo.slime.SlimeUtils;
import com.yahoo.vespa.hosted.provision.os.OsVersionChange;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;

/**
 * Serializer for {@link OsVersionChange}.
 *
 * @author mpolden
 */
public class OsVersionChangeSerializer {

    private static final String TARGETS_FIELD = "targets";
    private static final String NODE_TYPE_FIELD = "nodeType";
    private static final String VERSION_FIELD = "version";

    private OsVersionChangeSerializer() {}

    public static byte[] toJson(OsVersionChange change) {
        var slime = new Slime();
        var object = slime.setObject();
        var targetsObject = object.setArray(TARGETS_FIELD);
        change.targets().forEach((nodeType, osVersion) -> {
            var targetObject = targetsObject.addObject();
            targetObject.setString(NODE_TYPE_FIELD, NodeSerializer.toString(nodeType));
            targetObject.setString(VERSION_FIELD, osVersion.toFullString());
            // TODO(mpolden): Stop writing old format after May 2020
            var versionObject = object.setObject(NodeSerializer.toString(nodeType));
            versionObject.setString(VERSION_FIELD, osVersion.toFullString());
        });
        try {
            return SlimeUtils.toJsonBytes(slime);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static OsVersionChange fromJson(byte[] data) {
        var targets = new HashMap<NodeType, Version>();
        var inspector = SlimeUtils.jsonToSlime(data).get();
        // TODO(mpolden): Remove reading of old format after May 2020
        inspector.traverse((ObjectTraverser) (key, value) -> {
            if (isNodeType(key)) {
                var version = Version.fromString(value.field(VERSION_FIELD).asString());
                targets.put(NodeSerializer.nodeTypeFromString(key), version);
            }
        });
        inspector.field(TARGETS_FIELD).traverse((ArrayTraverser) (idx, arrayInspector) -> {
            var version = Version.fromString(arrayInspector.field(VERSION_FIELD).asString());
            var nodeType = NodeSerializer.nodeTypeFromString(arrayInspector.field(NODE_TYPE_FIELD).asString());
            targets.put(nodeType, version);
        });
        return new OsVersionChange(targets);
    }

    private static boolean isNodeType(String name) {
        try {
            NodeType.valueOf(name);
            return true;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

}
