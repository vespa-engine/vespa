// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.hosted.controller.api.integration.athenz;

import com.yahoo.vespa.athenz.api.AthenzUser;

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

    public void addPendingMember(AthenzUser user) {
        pendingMembers.add(user);
    }
}
