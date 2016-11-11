// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.tenant;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Rotation;
import com.yahoo.path.Path;

import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.curator.transaction.CuratorOperations;
import com.yahoo.vespa.curator.transaction.CuratorTransaction;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Rotations for an application. Persisted in ZooKeeper.
 *
 * @author hmusum
 * @author bratseth
 */
// TODO: This should be owned by the correct Tenant object and the rotations set should be contained in this
public class Rotations {

    private final Path path;
    private final Curator curator;

    public Rotations(Curator curator, Path tenantPath) {
        this.curator = curator;
        this.path = tenantPath.append("rotationsCache/");
    }

    public Set<Rotation> readRotationsFromZooKeeper(ApplicationId application) {
        try {
            Optional<byte[]> data = curator.getData(rotationsOf(application));
            if ( ! data.isPresent() || data.get().length == 0) return Collections.emptySet();
            Set<String> rotationIds = new ObjectMapper().readValue(data.get(), new TypeReference<Set<String>>() {});
            return rotationIds.stream().map(Rotation::new).collect(Collectors.toSet());
        } catch (Exception e) {
            throw new RuntimeException("Error reading rotations of " + application, e);
        }
    }

    public void writeRotationsToZooKeeper(ApplicationId application, Set<Rotation> rotations) {
        if (rotations.isEmpty()) return;
        try {
            Set<String> rotationIds = rotations.stream().map(Rotation::getId).collect(Collectors.toSet());
            byte[] data = new ObjectMapper().writeValueAsBytes(rotationIds);
            curator.set(rotationsOf(application), data);
        } catch (Exception e) {
            throw new RuntimeException("Could not write rotations of " + application, e);
        }
    }

    /** Returns a transaction which deletes these rotations if they exist */
    public CuratorTransaction delete(ApplicationId application) {
        if ( ! curator.exists(rotationsOf(application))) return CuratorTransaction.empty(curator);
        return CuratorTransaction.from(CuratorOperations.delete(rotationsOf(application).getAbsolute()), curator);
    }
    
    /** Returns the path storing the rotations data for an application */
    private Path rotationsOf(ApplicationId application) {
        return path.append(application.serializedForm());
    }

}
