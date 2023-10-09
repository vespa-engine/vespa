// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.tenant;

import com.yahoo.config.model.api.ApplicationRoles;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.path.Path;
import com.yahoo.slime.Slime;
import com.yahoo.slime.SlimeUtils;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.curator.transaction.CuratorOperations;
import com.yahoo.vespa.curator.transaction.CuratorTransaction;

import java.util.Optional;

/**
 * Stores application roles for an application.
 * @author mortent
 */
 // TODO: Remove and clean up zk after 7.458
public class ApplicationRolesStore {

    private final Path path;
    private final Curator curator;

    public ApplicationRolesStore(Curator curator, Path tenantPath) {
        this.curator = curator;
        this.path = tenantPath.append("applicationRoles/");
    }

    /** Reads the application roles from ZooKeeper, if it exists */
    public Optional<ApplicationRoles> readApplicationRoles(ApplicationId application) {
        try {
            Optional<byte[]> data = curator.getData(applicationRolesPath(application));
            if (data.isEmpty() || data.get().length == 0) return Optional.empty();
            Slime slime = SlimeUtils.jsonToSlime(data.get());
            ApplicationRoles applicationRoles = ApplicationRolesSerializer.fromSlime(slime.get());
            return Optional.of(applicationRoles);
        } catch (Exception e) {
            throw new RuntimeException("Error reading application roles of " + application, e);
        }
    }

    /** Writes the application roles to ZooKeeper */
    public void writeApplicationRoles(ApplicationId application, ApplicationRoles applicationRoles) {
        try {
            Slime slime = new Slime();
            ApplicationRolesSerializer.toSlime(applicationRoles, slime.setObject());
            curator.set(applicationRolesPath(application), SlimeUtils.toJsonBytes(slime));
        } catch (Exception e) {
            throw new RuntimeException("Could not write application roles of " + application, e);
        }
    }

    /** Returns a transaction which deletes application roles if they exist */
    public CuratorTransaction delete(ApplicationId application) {
        if (!curator.exists(applicationRolesPath(application))) return CuratorTransaction.empty(curator);
        return CuratorTransaction.from(CuratorOperations.delete(applicationRolesPath(application).getAbsolute()), curator);
    }

    /** Returns the path storing the application roles for an application */
    private Path applicationRolesPath(ApplicationId application) {
        return path.append(application.serializedForm());
    }
}
