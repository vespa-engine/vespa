// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.tenant;

import com.yahoo.config.provision.TenantName;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * @author olaa
 */
public class PendingMailVerification {

    private final TenantName tenantName;
    private final String mailAddress;
    private final String verificationCode;
    private final Instant verificationDeadline;
    private final MailType mailType;

    public PendingMailVerification(TenantName tenantName, String mailAddress, String verificationCode, Instant verificationDeadline, MailType mailType) {
        this.tenantName = tenantName;
        this.mailAddress = mailAddress;
        this.verificationCode = verificationCode;
        this.verificationDeadline = verificationDeadline;
        this.mailType = mailType;
    }

    public TenantName getTenantName() {
        return tenantName;
    }

    public String getMailAddress() {
        return mailAddress;
    }

    public String getVerificationCode() {
        return verificationCode;
    }

    public Instant getVerificationDeadline() {
        return verificationDeadline;
    }

    public MailType getMailType() {
        return mailType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PendingMailVerification that = (PendingMailVerification) o;
        return Objects.equals(tenantName, that.tenantName) &&
                Objects.equals(mailAddress, that.mailAddress) &&
                Objects.equals(verificationCode, that.verificationCode) &&
                Objects.equals(verificationDeadline, that.verificationDeadline) &&
                mailType == that.mailType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(tenantName, mailAddress, verificationCode, verificationDeadline, mailType);
    }

    @Override
    public String toString() {
        return "PendingMailVerification{" +
                "tenantName=" + tenantName +
                ", mailAddress='" + mailAddress + '\'' +
                ", verificationCode='" + verificationCode + '\'' +
                ", verificationDeadline=" + verificationDeadline +
                ", mailType=" + mailType +
                '}';
    }

    public enum MailType {
        TENANT_CONTACT,
        NOTIFICATIONS
    }
}
