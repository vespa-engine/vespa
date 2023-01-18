// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.support.access;

import com.yahoo.transaction.Mutex;
import com.yahoo.vespa.athenz.api.AthenzUser;
import com.yahoo.vespa.curator.Lock;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.identifiers.DeploymentId;

import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.Period;
import java.util.List;
import java.util.stream.Collectors;

import static com.yahoo.vespa.hosted.controller.support.access.SupportAccess.State.ALLOWED;
import static com.yahoo.vespa.hosted.controller.support.access.SupportAccess.State.NOT_ALLOWED;

/**
 * Which application endpoints should Vespa support be allowed to access for debugging?
 *
 * @author andreer
 */
public class SupportAccessControl {

    private final Controller controller;

    private final java.time.Period MAX_SUPPORT_ACCESS_TIME = Period.ofDays(10);

    public SupportAccessControl(Controller controller) {
        this.controller = controller;
    }

    public SupportAccess forDeployment(DeploymentId deploymentId) {
        return controller.curator().readSupportAccess(deploymentId);
    }

    public SupportAccess disallow(DeploymentId deployment, String by) {
        try (Mutex lock = controller.curator().lockSupportAccess(deployment)) {
            var now = controller.clock().instant();
            SupportAccess supportAccess = forDeployment(deployment);
            if (supportAccess.currentStatus(now).state() == NOT_ALLOWED) {
                throw new IllegalArgumentException("Support access is no longer allowed");
            } else {
                var disallowed = supportAccess.withDisallowed(by, now);
                controller.curator().writeSupportAccess(deployment, disallowed);
                return disallowed;
            }
        }
    }

    public SupportAccess allow(DeploymentId deployment, Instant until, String by) {
        try (Mutex lock = controller.curator().lockSupportAccess(deployment)) {
            var now = controller.clock().instant();
            if (until.isAfter(now.plus(MAX_SUPPORT_ACCESS_TIME))) {
                throw new IllegalArgumentException("Support access cannot be allowed for more than 10 days");
            }
            SupportAccess allowed = forDeployment(deployment).withAllowedUntil(until, by, now);
            controller.curator().writeSupportAccess(deployment, allowed);
            return allowed;
        }
    }

    public SupportAccess registerGrant(DeploymentId deployment, String by, X509Certificate certificate) {
        try (Mutex lock = controller.curator().lockSupportAccess(deployment)) {
            var now = controller.clock().instant();
            SupportAccess supportAccess = forDeployment(deployment);
            if (certificate.getNotAfter().toInstant().isBefore(now)) {
                throw new IllegalArgumentException("Support access certificate has already expired!");
            }
            if (certificate.getNotAfter().toInstant().isAfter(now.plus(MAX_SUPPORT_ACCESS_TIME))) {
                throw new IllegalArgumentException("Support access certificate validity time is limited to " + MAX_SUPPORT_ACCESS_TIME);
            }
            if (supportAccess.currentStatus(now).state() == NOT_ALLOWED) {
                throw new IllegalArgumentException("Support access is not currently allowed by " + deployment.toUserFriendlyString());
            }
            SupportAccess granted = supportAccess.withGrant(new SupportAccessGrant(by, certificate));
            controller.curator().writeSupportAccess(deployment, granted);
            return granted;
        }
    }

    public List<SupportAccessGrant> activeGrantsFor(DeploymentId deployment) {
        var now = controller.clock().instant();
        SupportAccess supportAccess = forDeployment(deployment);
        if (supportAccess.currentStatus(now).state() == NOT_ALLOWED) return List.of();

        return supportAccess.grantHistory().stream()
                .filter(grant -> now.isAfter(grant.certificate().getNotBefore().toInstant()))
                .filter(grant -> now.isBefore(grant.certificate().getNotAfter().toInstant()))
                .toList();
    }

    public boolean allowDataplaneMembership(AthenzUser identity, DeploymentId deploymentId) {
        Instant instant = controller.clock().instant();
        SupportAccess supportAccess = forDeployment(deploymentId);
        SupportAccess.CurrentStatus currentStatus = supportAccess.currentStatus(instant);
        if(currentStatus.state() == ALLOWED) {
            return controller.serviceRegistry().accessControlService().approveDataPlaneAccess(identity, currentStatus.allowedUntil().orElse(instant.plus(MAX_SUPPORT_ACCESS_TIME)));
        } else {
            return false;
        }
    }
}
