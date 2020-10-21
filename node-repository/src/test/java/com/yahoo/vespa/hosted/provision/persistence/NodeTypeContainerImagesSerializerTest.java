// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.persistence;

import com.yahoo.config.provision.DockerImage;
import com.yahoo.config.provision.NodeType;
import org.junit.Test;

import java.util.Map;
import java.util.TreeMap;

import static org.junit.Assert.assertEquals;

/**
 * @author freva
 */
public class NodeTypeContainerImagesSerializerTest {

    @Test
    public void test_serialization() {
        Map<NodeType, DockerImage> images = new TreeMap<>();
        images.put(NodeType.host, DockerImage.fromString("docker.domain.tld/my/repo:1.2.3"));
        images.put(NodeType.confighost, DockerImage.fromString("docker.domain.tld/my/image:2.1"));

        Map<NodeType, DockerImage> serialized = NodeTypeContainerImagesSerializer.fromJson(NodeTypeContainerImagesSerializer.toJson(images));
        assertEquals(images, serialized);
    }

}
