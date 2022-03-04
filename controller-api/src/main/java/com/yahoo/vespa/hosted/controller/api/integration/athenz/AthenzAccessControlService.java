// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.hosted.controller.api.integration.athenz;

import com.yahoo.config.provision.TenantName;
import com.yahoo.vespa.athenz.api.AthenzDomain;
import com.yahoo.vespa.athenz.api.AthenzGroup;
import com.yahoo.vespa.athenz.api.AthenzIdentity;
import com.yahoo.vespa.athenz.api.AthenzRole;
import com.yahoo.vespa.athenz.api.AthenzRoleInformation;
import com.yahoo.vespa.athenz.api.AthenzUser;
import com.yahoo.vespa.athenz.api.OAuthCredentials;
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
    private final AthenzInstanceSynchronizer athenzInstanceSynchronizer;


    public AthenzAccessControlService(ZmsClient zmsClient, AthenzDomain domain, ZmsClient vespaZmsClient, AthenzInstanceSynchronizer athenzInstanceSynchronizer) {
        this.zmsClient = zmsClient;
        this.vespaZmsClient = vespaZmsClient;
        this.athenzInstanceSynchronizer = athenzInstanceSynchronizer;
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
            zmsClient.decidePendingRoleMembership(dataPlaneAccessRole, user, expiry, Optional.empty(), Optional.empty(), true);
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
    public AthenzRoleInformation getAccessRoleInformation(TenantName tenantName) {
        var role = sshRole(tenantName);
        if (!vespaZmsClient.listRoles(role.domain()).contains(role))
            vespaZmsClient.createRole(role, Map.of());

        return vespaZmsClient.getFullRoleInformation(role);
    }

    /**
     * @return true if access has been granted - false if already member
     */
    @Override
    public boolean decideSshAccess(TenantName tenantName, Instant expiry, OAuthCredentials oAuthCredentials, boolean approve) {
        var role = sshRole(tenantName);

        if (!vespaZmsClient.listRoles(role.domain()).contains(role))
            vespaZmsClient.createRole(role, Map.of());

        if (vespaZmsClient.getMembership(role, vespaTeam))
            return false;

        var roleInformation = vespaZmsClient.getFullRoleInformation(role);
        if (roleInformation.getPendingRequest().isEmpty())
            return false;
        var reason = roleInformation.getPendingRequest().get().getReason();

        vespaZmsClient.decidePendingRoleMembership(role, vespaTeam, expiry, Optional.of(reason), Optional.of(oAuthCredentials), approve);
        athenzInstanceSynchronizer.synchronizeInstances(tenantName);
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

    public void setPreapprovedAccess(TenantName tenantName, boolean preapprovedAccess) {
        var role = sshRole(tenantName);

        var attributes = Map.<String, Object>of(
                "selfServe", !preapprovedAccess,
                "reviewEnabled", !preapprovedAccess
        );
        vespaZmsClient.createRole(role, attributes);
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
