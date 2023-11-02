// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.hosted.controller.tenant;

import com.yahoo.vespa.hosted.controller.api.role.SimplePrincipal;

import java.time.Instant;
import java.util.Optional;

/**
 * @author bjorncs
 */
public record TermsOfServiceApproval(Instant approvedAt, Optional<SimplePrincipal> approvedBy) {

    public TermsOfServiceApproval(Instant at, SimplePrincipal by) { this(at, Optional.of(by)); }

    public TermsOfServiceApproval(String at, String by) {
        this(at.isBlank() ? Instant.EPOCH : Instant.parse(at), by.isBlank() ? Optional.empty() : Optional.of(new SimplePrincipal(by)));
    }

    public TermsOfServiceApproval {
        if (approvedBy.isEmpty() && !Instant.EPOCH.equals(approvedAt))
            throw new IllegalArgumentException("Missing approver");
    }

    public static TermsOfServiceApproval empty() { return new TermsOfServiceApproval(Instant.EPOCH, Optional.empty()); }

    public boolean hasApproved() { return approvedBy.isPresent(); }
    public boolean isEmpty() { return approvedBy.isEmpty() && Instant.EPOCH.equals(approvedAt); }
}
