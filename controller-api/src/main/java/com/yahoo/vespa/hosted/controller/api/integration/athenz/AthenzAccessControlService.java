// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.hosted.controller.api.integration.athenz;

import com.yahoo.vespa.athenz.api.AthenzDomain;
import com.yahoo.vespa.athenz.api.AthenzRole;
import com.yahoo.vespa.athenz.api.AthenzUser;
import com.yahoo.vespa.athenz.client.zms.ZmsClient;

import java.util.Collection;
import java.util.List;

public class AthenzAccessControlService implements AccessControlService {

    private static final String DATAPLANE_ACCESS_ROLENAME = "operator-data-plane";
    private final ZmsClient zmsClient;
    private final AthenzRole dataPlaneAccessRole;


    public AthenzAccessControlService(ZmsClient zmsClient, AthenzDomain domain) {
        this.zmsClient = zmsClient;
        this.dataPlaneAccessRole = new AthenzRole(domain, DATAPLANE_ACCESS_ROLENAME);
    }

    @Override
    public boolean approveDataPlaneAccess(AthenzUser user) {
        List<AthenzUser> users = zmsClient.listPendingRoleApprovals(dataPlaneAccessRole);
        if (users.contains(user)) {
            // TODO (mortent): Handle expiry
            zmsClient.approvePendingRoleMembership(dataPlaneAccessRole, user, null);
            return true;
        }
        return false;
    }

    @Override
    public Collection<AthenzUser> listMembers() {
        return null;
    }
}
