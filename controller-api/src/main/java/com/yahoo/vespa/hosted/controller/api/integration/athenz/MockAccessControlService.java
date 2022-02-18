// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.hosted.controller.api.integration.athenz;

import com.yahoo.config.provision.TenantName;
import com.yahoo.vespa.athenz.api.AthenzUser;
import com.yahoo.vespa.athenz.api.OAuthCredentials;

import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class MockAccessControlService implements AccessControlService {

    private final Set<AthenzUser> pendingMembers = new HashSet<>();
    private final Set<AthenzUser> members = new HashSet<>();

    @Override
    public boolean approveDataPlaneAccess(AthenzUser user, Instant expiry) {
        if (pendingMembers.remove(user)) {
            return members.add(user);
        } else {
            return false;
        }
    }

    @Override
    public Collection<AthenzUser> listMembers() {
        return Set.copyOf(members);
    }

    @Override
    public boolean approveSshAccess(TenantName tenantName, Instant expiry, OAuthCredentials oAuthCredentials) {
        return false;
    }

    @Override
    public boolean requestSshAccess(TenantName tenantName) {
        return false;
    }

    @Override
    public boolean hasPendingAccessRequests(TenantName tenantName) {
        return false;
    }

    public void addPendingMember(AthenzUser user) {
        pendingMembers.add(user);
    }
}
