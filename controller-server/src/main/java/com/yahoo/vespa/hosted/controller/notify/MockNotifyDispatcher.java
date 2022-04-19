package com.yahoo.vespa.hosted.controller.notify;

import com.yahoo.vespa.hosted.controller.api.integration.notification.Notification;
import com.yahoo.vespa.hosted.controller.api.integration.notify.NotifyDispatcher;
import com.yahoo.vespa.hosted.controller.tenant.TenantContacts;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author enygaard
 */
public class MockNotifyDispatcher implements NotifyDispatcher {
    private final Map<String, List<Notification>> mails;

    public MockNotifyDispatcher() {
        mails = new HashMap<>();
    }

    @Override
    public void mail(Notification notification, Collection<TenantContacts.EmailContact> collect) {
        collect.forEach(c -> {
            var list = mails.putIfAbsent(c.email(), new ArrayList<>());
            mails.get(c.email()).add(notification);
        });
    }

    public List<Notification> inbox(String email) {
        return mails.getOrDefault(email, List.of());
    }

    public void reset() {
        mails.clear();
    }
}
