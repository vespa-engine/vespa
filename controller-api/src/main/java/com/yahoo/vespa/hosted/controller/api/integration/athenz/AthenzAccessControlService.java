// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.hosted.controller.api.integration.athenz;

import com.yahoo.config.provision.TenantName;
import com.yahoo.vespa.athenz.api.AthenzDomain;
import com.yahoo.vespa.athenz.api.AthenzGroup;
import com.yahoo.vespa.athenz.api.AthenzIdentity;
import com.yahoo.vespa.athenz.api.AthenzRole;
import com.yahoo.vespa.athenz.api.AthenzUser;
import com.yahoo.vespa.athenz.client.zms.ZmsClient;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class AthenzAccessControlService implements AccessControlService {

    private static final String ALLOWED_OPERATOR_GROUPNAME = "vespa-team";
    private static final String DATAPLANE_ACCESS_ROLENAME = "operator-data-plane";
    private final String TENANT_DOMAIN_PREFIX = "vespa.tenant";
    private final ZmsClient zmsClient;
    private final AthenzRole dataPlaneAccessRole;
    private final AthenzGroup vespaTeam;
    private final ZmsClient vespaZmsClient; //TODO: Merge ZMS clients


    public AthenzAccessControlService(ZmsClient zmsClient, AthenzDomain domain, ZmsClient vespaZmsClient) {
        this.zmsClient = zmsClient;
        this.vespaZmsClient = vespaZmsClient;
        this.dataPlaneAccessRole = new AthenzRole(domain, DATAPLANE_ACCESS_ROLENAME);
        this.vespaTeam = new AthenzGroup(domain, ALLOWED_OPERATOR_GROUPNAME);
    }

    @Override
    public boolean approveDataPlaneAccess(AthenzUser user, Instant expiry) {
        // Can only approve team members, other members must be manually approved
        if(!isVespaTeamMember(user)) {
            throw new IllegalArgumentException(String.format("User %s requires manual approval, please contact Vespa team", user.getName()));
        }
        Map<AthenzIdentity, String> users = zmsClient.listPendingRoleApprovals(dataPlaneAccessRole);
        if (users.containsKey(user)) {
            zmsClient.approvePendingRoleMembership(dataPlaneAccessRole, user, expiry, Optional.empty());
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

    /**
     * @return Whether the ssh access role has any pending role membership requests
     */
    @Override
    public boolean hasPendingAccessRequests(TenantName tenantName) {
        var role = sshRole(tenantName);
        if (!vespaZmsClient.listRoles(role.domain()).contains(role))
            return false;
        var pendingApprovals = vespaZmsClient.listPendingRoleApprovals(role);
        return pendingApprovals.containsKey(vespaTeam);
    }

    /**
     * @return true if access has been granted - false if already member
     */
    @Override
    public boolean approveSshAccess(TenantName tenantName, Instant expiry) {
        var role = sshRole(tenantName);

        if (!vespaZmsClient.listRoles(role.domain()).contains(role))
            vespaZmsClient.createRole(role, Map.of());

        if (vespaZmsClient.getMembership(role, vespaTeam))
            return false;

        if (!hasPendingAccessRequests(tenantName)) {
            vespaZmsClient.addRoleMember(role, vespaTeam, Optional.empty());
        }
        // TODO: Pass along auth0 credentials
        vespaZmsClient.approvePendingRoleMembership(role, vespaTeam, expiry, Optional.empty());
        return true;
    }

    /**
     * @return true if access has been requested - false if already member
     */
    @Override
    public boolean requestSshAccess(TenantName tenantName) {
        var role = sshRole(tenantName);

        if (!vespaZmsClient.listRoles(role.domain()).contains(role))
            vespaZmsClient.createRole(role, Map.of());

        if (vespaZmsClient.getMembership(role, vespaTeam))
            return false;

        vespaZmsClient.addRoleMember(role, vespaTeam, Optional.empty());
        return true;
    }

    private AthenzRole sshRole(TenantName tenantName) {
        return new AthenzRole(getOrCreateTenantDomain(tenantName), "ssh_access");
    }

    private AthenzDomain getOrCreateTenantDomain(TenantName tenantName) {
        var domain = new AthenzDomain(TENANT_DOMAIN_PREFIX + "." + tenantName.value());

        if (vespaZmsClient.getDomainList(domain.getName()).isEmpty()) {
            vespaZmsClient.createSubdomain(new AthenzDomain(TENANT_DOMAIN_PREFIX), tenantName.value());
        }

        return domain;
    }

    public boolean isVespaTeamMember(AthenzUser user) {
        return zmsClient.getGroupMembership(vespaTeam, user);
    }
}
