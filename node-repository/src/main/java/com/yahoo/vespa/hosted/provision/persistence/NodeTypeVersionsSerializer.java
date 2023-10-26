// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.persistence;

import com.yahoo.component.Version;
import com.yahoo.config.provision.NodeType;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.ObjectTraverser;
import com.yahoo.slime.Slime;
import com.yahoo.slime.SlimeUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.TreeMap;

/**
 * Serializer for version numbers that are set per node type.
 *
 * @author freva
 * @author mpolden
 */
public class NodeTypeVersionsSerializer {

    private NodeTypeVersionsSerializer() {}

    public static byte[] toJson(Map<NodeType, Version> versions) {
        Slime slime = new Slime();
        Cursor object = slime.setObject();
        versions.forEach((nodeType, version) -> object.setString(NodeSerializer.toString(nodeType),
                                                                 version.toFullString()));
        try {
            return SlimeUtils.toJsonBytes(slime);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static Map<NodeType, Version> fromJson(byte[] data) {
        Map<NodeType, Version> versions = new TreeMap<>(); // Use TreeMap to sort by node type
        Inspector inspector = SlimeUtils.jsonToSlime(data).get();
        inspector.traverse((ObjectTraverser) (key, value) ->
                versions.put(NodeSerializer.nodeTypeFromString(key), Version.fromString(value.asString())));
        return versions;
    }

}
