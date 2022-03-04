// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.api;

import com.yahoo.vespa.athenz.client.zms.bindings.RoleEntity;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * @author olaa
 */
public class AthenzRoleInformation extends AthenzRole {

    private final boolean isSelfServe;
    private final boolean reviewEnabled;
    private final Optional<MembershipRequest> pendingRequest;
    private final List<AuditLogEntry> auditLog;

    public AthenzRoleInformation(AthenzDomain domain, String roleName, boolean isSelfServe, boolean reviewEnabled, Optional<MembershipRequest> pendingRequest, List<AuditLogEntry> auditLog) {
        super(domain, roleName);
        this.isSelfServe = isSelfServe;
        this.reviewEnabled = reviewEnabled;
        this.pendingRequest = pendingRequest;
        this.auditLog = auditLog;
    }

    public boolean isSelfServe() {
        return isSelfServe;
    }

    public boolean isReviewEnabled() {
        return reviewEnabled;
    }

    public Optional<MembershipRequest> getPendingRequest() {
        return pendingRequest;
    }

    public List<AuditLogEntry> getAuditLog() {
        return auditLog;
    }

    public static AthenzRoleInformation fromRoleEntity(RoleEntity roleEntity) {
        var role = fromResourceNameString(roleEntity.roleName());
        var isSelfServe = roleEntity.selfServe() != null && roleEntity.selfServe();
        var reviewEnabled = roleEntity.reviewEnabled() != null && roleEntity.reviewEnabled();
        var pendingRequest = roleEntity.roleMembers()
                .stream()
                .filter(member -> member.pendingApproval())
                .map(member -> new MembershipRequest(member.memberName(), member.auditRef(), member.requestTime(), member.active()))
                .findFirst();
        var auditLog = roleEntity.auditLog()
                .stream()
                .map(entry -> new AuditLogEntry(entry.getAdmin(), entry.getAction(), entry.getAuditRef(), entry.getCreated()))
                .collect(Collectors.toList());
        return new AthenzRoleInformation(role.domain(), role.roleName(), isSelfServe, reviewEnabled, pendingRequest, auditLog);
    }


    public static class MembershipRequest {
        private final String memberName;
        private final String reason;
        private final String creationTime;
        private final boolean active;

        public MembershipRequest(String memberName, String reason, String creationTime, boolean active) {
            this.memberName = memberName;
            this.reason = reason;
            this.creationTime = creationTime;
            this.active = active;
        }

        public String getMemberName() {
            return memberName;
        }

        public String getReason() {
            return reason;
        }

        public String getCreationTime() {
            return creationTime;
        }

        public boolean isActive() {
            return active;
        }
    }

    public static class AuditLogEntry {
        private final String approver;
        private final String action;
        private final String reason;
        private final String creationTime;

        public AuditLogEntry(String approver, String action, String reason, String creationTime) {
            this.approver = approver;
            this.action = action;
            this.reason = reason;
            this.creationTime = creationTime;
        }

        public String getApprover() {
            return approver;
        }

        public String getAction() {
            return action;
        }

        public String getReason() {
            return reason;
        }

        public String getCreationTime() {
            return creationTime;
        }
    }

}
