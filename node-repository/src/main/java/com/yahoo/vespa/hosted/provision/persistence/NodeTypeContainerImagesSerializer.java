// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.persistence;

import com.yahoo.config.provision.DockerImage;
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
 * Serializer for docker images that are set per node type.
 *
 * @author freva
 */
public class NodeTypeContainerImagesSerializer {

    private NodeTypeContainerImagesSerializer() {}

    public static byte[] toJson(Map<NodeType, DockerImage> dockerImages) {
        Slime slime = new Slime();
        Cursor object = slime.setObject();
        dockerImages.forEach((nodeType, dockerImage) ->
                object.setString(NodeSerializer.toString(nodeType), dockerImage.asString()));
        try {
            return SlimeUtils.toJsonBytes(slime);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static Map<NodeType, DockerImage> fromJson(byte[] data) {
        Map<NodeType, DockerImage> images = new TreeMap<>(); // Use TreeMap to sort by node type
        Inspector inspector = SlimeUtils.jsonToSlime(data).get();
        inspector.traverse((ObjectTraverser) (key, value) ->
                images.put(NodeSerializer.nodeTypeFromString(key), DockerImage.fromString(value.asString())));
        return images;
    }

}
