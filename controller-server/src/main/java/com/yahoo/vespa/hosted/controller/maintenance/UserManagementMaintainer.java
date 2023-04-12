// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.integration.user.RoleMaintainer;

import java.time.Duration;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.stream.Collectors;


/**
 * Maintains user management resources.
 * For now, ensures there's no discrepnacy between expected tenant/application roles and auth0/athenz roles
 *
 * @author olaa
 */
public class UserManagementMaintainer extends ControllerMaintainer {

    private final RoleMaintainer roleMaintainer;
    private static final Logger logger = Logger.getLogger(UserManagementMaintainer.class.getName());

    public UserManagementMaintainer(Controller controller, Duration interval, RoleMaintainer roleMaintainer) {
        super(controller, interval);
        this.roleMaintainer = roleMaintainer;
    }

    @Override
    protected double maintain() {
        var tenants = controller().tenants().asList();
        var applications = controller().applications().idList()
                .stream()
                .map(appId -> ApplicationId.from(appId.tenant(), appId.application(), InstanceName.defaultName()))
                .toList();
        roleMaintainer.deleteLeftoverRoles(tenants, applications);

        if (!controller().system().isPublic()) {
            roleMaintainer.tenantsToDelete(tenants)
                    .forEach(tenant -> {
                        logger.warning(tenant.name() + " has a non-existing Athenz domain. Deleting");
                        controller().applications().asList(tenant.name())
                                .forEach(application -> controller().applications().deleteApplication(application.id(), Optional.empty()));
                        controller().tenants().delete(tenant.name(), Optional.empty(), false);
                    });
        }

        return 0.0;
    }

}
