// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.hosted.controller.api.integration.athenz;

import com.yahoo.config.provision.TenantName;
import com.yahoo.vespa.athenz.api.AthenzAssertion;
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
    private final String ACCESS_APPROVAL_POLICY = "vespa-access-requester";
    private final ZmsClient zmsClient;
    private final AthenzRole dataPlaneAccessRole;
    private final AthenzGroup vespaTeam;
    private final Optional<ZmsClient> vespaZmsClient;
    private final AthenzInstanceSynchronizer athenzInstanceSynchronizer;


    public AthenzAccessControlService(ZmsClient zmsClient, AthenzDomain domain, Optional<ZmsClient> vespaZmsClient, AthenzInstanceSynchronizer athenzInstanceSynchronizer) {
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
        return vespaZmsClient.map(
                zms -> {
                    var role = sshRole(tenantName);
                    return zms.getFullRoleInformation(role);
                }
        ).orElseThrow(() -> new UnsupportedOperationException("Only allowed in systems running Vespa Athenz instance"));

    }

    /**
     * @return true if access has been granted - false if already member
     */
    @Override
    public boolean decideSshAccess(TenantName tenantName, Instant expiry, OAuthCredentials oAuthCredentials, boolean approve) {
        return vespaZmsClient.map(
                zms -> {
                    var role = sshRole(tenantName);

                    var roleInformation = zms.getFullRoleInformation(role);
                    if (roleInformation.getPendingRequest().isEmpty())
                        return false;
                    var reason = roleInformation.getPendingRequest().get().getReason();

                    zms.decidePendingRoleMembership(role, vespaTeam, expiry, Optional.of(reason), Optional.of(oAuthCredentials), approve);
                    if (approve) athenzInstanceSynchronizer.synchronizeInstances(tenantName);
                    return true;
                }
        ).orElseThrow(() -> new UnsupportedOperationException("Only allowed in systems running Vespa Athenz instance"));
    }

    /**
     * @return true if access has been requested - false if already member
     */
    @Override
    public boolean requestSshAccess(TenantName tenantName) {
        return vespaZmsClient.map(
                zms -> {
                    var role = sshRole(tenantName);

                    if (zms.getMembership(role, vespaTeam))
                        return false;

                    zms.addRoleMember(role, vespaTeam, Optional.empty());
                    return true;
                }
        ).orElseThrow(() -> new UnsupportedOperationException("Only allowed in systems running Vespa Athenz instance"));
    }

    public void setManagedAccess(TenantName tenantName, boolean managedAccess) {
        vespaZmsClient.ifPresentOrElse(
                zms -> {
                    var role = sshRole(tenantName);
                    var assertion = getApprovalAssertion(role);
                    if (managedAccess) {
                        zms.deletePolicyRule(role.domain(), ACCESS_APPROVAL_POLICY, assertion.action(), assertion.resource(), assertion.role());
                    } else {
                        zms.addPolicyRule(role.domain(), ACCESS_APPROVAL_POLICY, assertion.action(), assertion.resource(), assertion.role());
                    }
                },() -> { throw new UnsupportedOperationException("Only allowed in systems running Vespa Athenz instance"); });
    }

    public boolean getManagedAccess(TenantName tenantName) {
        return vespaZmsClient.map(
                zms -> {
                    var role = sshRole(tenantName);
                    var approvalAssertion = getApprovalAssertion(role);
                    return zms.getPolicy(role.domain(), ACCESS_APPROVAL_POLICY)
                            .map(policy -> policy.assertions().stream().noneMatch(assertion -> assertion.satisfies(approvalAssertion)))
                            .orElse(true);
                }).orElseThrow(() -> new UnsupportedOperationException("Only allowed in systems running Vespa Athenz instance") );
    }

    private AthenzRole sshRole(TenantName tenantName) {
        return new AthenzRole(getTenantDomain(tenantName), "ssh_access");
    }

    private AthenzDomain getTenantDomain(TenantName tenantName) {
        return new AthenzDomain(TENANT_DOMAIN_PREFIX + "." + tenantName.value());
    }

    public boolean isVespaTeamMember(AthenzUser user) {
        return zmsClient.getGroupMembership(vespaTeam, user);
    }

    private AthenzAssertion getApprovalAssertion(AthenzRole accessRole) {
        var approverRole = new AthenzRole(accessRole.domain(), "vespa-access-approver");
        return AthenzAssertion.newBuilder(approverRole, accessRole.toResourceName(), "update_members")
                .effect(AthenzAssertion.Effect.ALLOW)
                .build();
    }
}
