// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.athenz.client.zms.bindings;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * @author mortent
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class RoleEntity {
    private final String roleName;
    private final List<Member> roleMembers;
    private final Boolean selfServe;
    private final Boolean reviewEnabled;
    private final List<AuditLogEntry> auditLog;

    @JsonCreator
    public RoleEntity(@JsonProperty("name") String roleName,
                      @JsonProperty("roleMembers") List<Member> roleMembers,
                      @JsonProperty("selfServe") Boolean selfServe,
                      @JsonProperty("reviewEnabled") Boolean reviewEnabled,
                      @JsonProperty("auditLog") List<AuditLogEntry> auditLog) {
        this.roleName = roleName;
        this.roleMembers = Optional.ofNullable(roleMembers).orElse(new ArrayList<>());
        this.selfServe = Optional.ofNullable(selfServe).orElse(false);
        this.reviewEnabled = Optional.ofNullable(reviewEnabled).orElse(false);
        this.auditLog = Optional.ofNullable(auditLog).orElse(new ArrayList<>());;
    }

    public String roleName() {
        return roleName;
    }

    public List<Member> roleMembers() {
        return roleMembers;
    }

    public Boolean selfServe() {
        return selfServe;
    }

    public Boolean reviewEnabled() {
        return reviewEnabled;
    }

    public List<AuditLogEntry> auditLog() {
        return auditLog;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class Member {
        private final String memberName;
        private final boolean active;
        private final boolean approved;
        private final String auditRef;
        private final String requestTime;

        @JsonCreator
        public Member(@JsonProperty("memberName") String memberName,
                      @JsonProperty("active") boolean active,
                      @JsonProperty("approved") boolean approved,
                      @JsonProperty("auditRef") String auditRef,
                      @JsonProperty("requestTime") String requestTime) {
            this.memberName = memberName;
            this.active = active;
            this.approved = approved;
            this.auditRef = auditRef;
            this.requestTime = requestTime;
        }

        public String memberName() {
            return memberName;
        }

        public boolean pendingApproval() {
            return !approved;
        }

        public String auditRef() {
            return auditRef;
        }

        public String requestTime() {
            return requestTime;
        }

        public boolean active() {
            return active;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class AuditLogEntry {
        private final String member;
        private final String admin;
        private final String action;
        private final String auditRef;
        private final String created;

        @JsonCreator
        public AuditLogEntry(@JsonProperty("member") String member,
                             @JsonProperty("admin") String admin,
                             @JsonProperty("created") String created,
                             @JsonProperty("action") String action,
                             @JsonProperty("auditRef") String auditRef) {
            this.member = member;
            this.admin = admin;
            this.created = created;
            this.action = action;
            this.auditRef = auditRef;
        }

        public String getMember() {
            return member;
        }

        public String getAdmin() {
            return admin;
        }

        public String getAction() {
            return action;
        }

        public String getAuditRef() {
            return auditRef;
        }

        public String getCreated() {
            return created;
        }
    }
}
