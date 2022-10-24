// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.persistence;

import com.yahoo.config.provision.TenantName;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.hosted.controller.tenant.PendingMailVerification;

import java.time.Instant;

/**
 * @author olaa
 */
public class MailVerificationSerializer {

    private static final String tenantField = "tenant";
    private static final String audiencesField = "audiences";
    private static final String emailField = "email";
    private static final String emailTypeField = "emailType";
    private static final String emailVerificationCodeField = "emailVerificationCode";
    private static final String emailVerificationDeadlineField = "emailVerificationDeadline";
    private static final String rolesField = "roles";

    public static Slime toSlime(PendingMailVerification pendingMailVerification) {
        var slime = new Slime();
        var object = slime.setObject();
        toSlime(pendingMailVerification, object);
        return slime;
    }

    public static void toSlime(PendingMailVerification pendingMailVerification, Cursor object) {
        object.setString(tenantField, pendingMailVerification.getTenantName().value());
        object.setString(emailVerificationCodeField, pendingMailVerification.getVerificationCode());
        object.setString(emailField, pendingMailVerification.getMailAddress());
        object.setLong(emailVerificationDeadlineField, pendingMailVerification.getVerificationDeadline().toEpochMilli());
        object.setString(emailTypeField, pendingMailVerification.getMailType().name());
    }

    public static PendingMailVerification fromSlime(Slime slime) {
        return fromSlime(slime.get());
    }

    public static PendingMailVerification fromSlime(Inspector inspector) {
        var tenant = TenantName.from(inspector.field(tenantField).asString());
        var address = inspector.field(emailField).asString();
        var verificationCode = inspector.field(emailVerificationCodeField).asString();
        var deadline = Instant.ofEpochMilli(inspector.field(emailVerificationDeadlineField).asLong());
        var type = PendingMailVerification.MailType.valueOf(inspector.field(emailTypeField).asString());
        return new PendingMailVerification(tenant, address, verificationCode, deadline, type);
    }

}
