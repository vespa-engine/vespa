// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.tenant;

import com.yahoo.config.model.api.ApplicationRoles;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.path.Path;
import com.yahoo.vespa.curator.mock.MockCurator;
import org.junit.Test;

import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author mortent
 */
public class ApplicationRolesStoreTest {
    @Test
    public void persists_entry_correctly() {
        // Persist
        var applicationRolesStore = new ApplicationRolesStore(new MockCurator(), Path.createRoot());
        var roles = new ApplicationRoles("hostRole", "containerRole");
        applicationRolesStore.writeApplicationRoles(ApplicationId.defaultId(), roles);

        // Read
        Optional<ApplicationRoles> deserialized = applicationRolesStore.readApplicationRoles(ApplicationId.defaultId());
        assertTrue(deserialized.isPresent());
        assertEquals(roles, deserialized.get());
    }

    @Test
    public void read_non_existent() {
        var applicationRolesStore = new ApplicationRolesStore(new MockCurator(), Path.createRoot());
        Optional<ApplicationRoles> applicationRoles = applicationRolesStore.readApplicationRoles(ApplicationId.defaultId());
        assertTrue(applicationRoles.isEmpty());
    }

}
