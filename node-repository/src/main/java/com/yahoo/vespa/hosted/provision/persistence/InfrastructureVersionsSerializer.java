// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.persistence;

import com.yahoo.component.Version;
import com.yahoo.config.provision.NodeType;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.ObjectTraverser;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.config.SlimeUtils;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * @author freva
 */
class InfrastructureVersionsSerializer {

    private InfrastructureVersionsSerializer() {}

    static byte[] toJson(Map<NodeType, Version> versionsByNodeType) {
        try {
            Slime slime = new Slime();
            Cursor object = slime.setObject();
            versionsByNodeType.forEach((nodeType, version) ->
                    object.setString(NodeSerializer.toString(nodeType), version.toFullString()));
            return SlimeUtils.toJsonBytes(slime);
        } catch (IOException e) {
            throw new RuntimeException("Serialization of a infrastructure version failed", e);
        }
    }

    static Map<NodeType, Version> fromJson(byte[] data) {
        Map<NodeType, Version> infrastructureVersions = new HashMap<>();
        Inspector inspector = SlimeUtils.jsonToSlime(data).get();
        inspector.traverse((ObjectTraverser) (key, value) ->
                infrastructureVersions.put(NodeSerializer.nodeTypeFromString(key), Version.fromString(value.asString())));
        return infrastructureVersions;
    }
}
