// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Rotation;
import com.yahoo.path.Path;

import com.yahoo.vespa.curator.Curator;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Rotations for an application. Persisted in ZooKeeper.
 *
 * @author hmusum
 */
public class RotationsCache {

    private final Path path;
    private final Curator curator;

    public RotationsCache(Curator curator, Path tenantPath) {
        this.curator = curator;
        this.path = tenantPath.append("rotationsCache/");
    }

    public Set<Rotation> readRotationsFromZooKeeper(ApplicationId applicationId) {
        ObjectMapper objectMapper = new ObjectMapper();
        Path fullPath = path.append(applicationId.serializedForm());
        Set<Rotation> ret = new LinkedHashSet<>();
        try {
            if (curator != null && curator.exists(fullPath)) {
                byte[] data = curator.framework().getData().forPath(fullPath.getAbsolute());
                if (data.length > 0) {
                    Set<String> rotationIds = objectMapper.readValue(data, new TypeReference<Set<String>>() {
                    });
                    ret.addAll(rotationIds.stream().map(Rotation::new).collect(Collectors.toSet()));
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Error reading rotations from ZooKeeper (" + fullPath + ")", e);
        }
        return ret;
    }

    public void writeRotationsToZooKeeper(ApplicationId applicationId, Set<Rotation> rotations) {
        if (rotations.size() > 0) {
            final ObjectMapper objectMapper = new ObjectMapper();
            final Path cachePath = path.append(applicationId.serializedForm());
            final String absolutePath = cachePath.getAbsolute();
            try {
                curator.create(cachePath);
                final Set<String> rotationIds = rotations.stream().map(Rotation::getId).collect(Collectors.toSet());
                final byte[] data = objectMapper.writeValueAsBytes(rotationIds);
                curator.framework().setData().forPath(absolutePath, data);
            } catch (Exception e) {
                throw new RuntimeException("Error writing rotations to ZooKeeper (" + absolutePath + ")", e);
            }
        }
    }

    public void deleteRotationFromZooKeeper(ApplicationId applicationId) {
        curator.delete(path.append(applicationId.serializedForm()));
    }
}
