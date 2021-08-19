// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.hosted.controller.api.integration.athenz;

import com.yahoo.vespa.athenz.api.AthenzDomain;
import com.yahoo.vespa.athenz.api.AthenzGroup;
import com.yahoo.vespa.athenz.api.AthenzRole;
import com.yahoo.vespa.athenz.api.AthenzUser;
import com.yahoo.vespa.athenz.client.zms.ZmsClient;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class AthenzAccessControlService implements AccessControlService {

    private static final String ALLOWED_OPERATOR_GROUPNAME = "vespa-team";
    private static final String DATAPLANE_ACCESS_ROLENAME = "operator-data-plane";
    private final ZmsClient zmsClient;
    private final AthenzRole dataPlaneAccessRole;
    private final AthenzGroup vespaTeam;


    public AthenzAccessControlService(ZmsClient zmsClient, AthenzDomain domain) {
        this.zmsClient = zmsClient;
        this.dataPlaneAccessRole = new AthenzRole(domain, DATAPLANE_ACCESS_ROLENAME);
        this.vespaTeam = new AthenzGroup(domain, ALLOWED_OPERATOR_GROUPNAME);
    }

    @Override
    public boolean approveDataPlaneAccess(AthenzUser user, Instant expiry) {
        // Can only approve team members, other members must be manually approved
        if(!isVespaTeamMember(user)) {
            throw new IllegalArgumentException(String.format("User %s requires manual approval, please contact Vespa team", user.getName()));
        }
        Map<AthenzUser, String> users = zmsClient.listPendingRoleApprovals(dataPlaneAccessRole);
        if (users.containsKey(user)) {
            zmsClient.approvePendingRoleMembership(dataPlaneAccessRole, user, expiry);
            return true;
        }
        return false;
    }

    @Override
    // Return list of approved members (users, excluding services) of data plane role
    public Collection<AthenzUser> listMembers() {
        return zmsClient.listMembers(dataPlaneAccessRole)
                .stream().filter(AthenzUser.class::isInstance)
                .map(AthenzUser.class::cast)
                .collect(Collectors.toList());
    }

    public boolean isVespaTeamMember(AthenzUser user) {
        return zmsClient.getGroupMembership(vespaTeam, user);
    }
}
