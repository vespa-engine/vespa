// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.hosted.controller.api.integration.athenz;

import com.yahoo.vespa.athenz.api.AthenzUser;

import java.time.Instant;
import java.util.Collection;

/**
 * Manage operator data plane access control
 *
 * @author mortent
 */
public interface AccessControlService {
    boolean approveDataPlaneAccess(AthenzUser user, Instant expiry);
    Collection<AthenzUser> listMembers();
}
