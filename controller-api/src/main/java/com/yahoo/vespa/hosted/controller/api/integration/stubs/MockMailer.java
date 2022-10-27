// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.stubs;

import com.yahoo.vespa.hosted.controller.api.integration.organization.Mail;
import com.yahoo.vespa.hosted.controller.api.integration.organization.Mailer;
import com.yahoo.vespa.hosted.controller.tenant.PendingMailVerification;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MockMailer implements Mailer {

    public final Map<String, List<Mail>> mails = new HashMap<>();
    public final boolean blackhole;

    public MockMailer() {
        this(false);
    }

    MockMailer(boolean blackhole) {
        this.blackhole = blackhole;
    }

    public static MockMailer blackhole() {
        return new MockMailer(true);
    }

    @Override
    public void send(Mail mail) {
        if (blackhole) {
            return;
        }
        for (String recipient : mail.recipients()) {
            mails.putIfAbsent(recipient, new ArrayList<>());
            mails.get(recipient).add(mail);
        }
    }

    @Override
    public String user() {
        return "user";
    }

    @Override
    public String domain() {
        return "domain";
    }

    @Override
    public void sendVerificationMail(PendingMailVerification pendingMailVerification) {
        send(new Mail(List.of(pendingMailVerification.getMailAddress()), "subject", "message"));
    }

    /** Returns the list of mails sent to the given recipient. Modifications affect the set of mails stored in this. */
    public List<Mail> inbox(String recipient) {
        return mails.getOrDefault(recipient, List.of());
    }

    public void reset() {
        mails.clear();
    }
}
