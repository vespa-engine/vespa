package com.yahoo.vespa.hosted.controller.api.integration.organization;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MockMailer implements Mailer {

    public final Map<String, List<Mail>> mails = new HashMap<>();

    @Override
    public void send(Mail mail) {
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

}
