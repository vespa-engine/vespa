package com.yahoo.vespa.hosted.controller.api.integration.notify;

import com.yahoo.vespa.hosted.controller.api.integration.notification.Notification;
import com.yahoo.vespa.hosted.controller.tenant.TenantContacts;

import java.util.Collection;

/**
 * NotifyDispatcher defines the notify API
 *
 * @author enygaard
 */
public interface NotifyDispatcher {
    void mail(Notification notification, Collection<TenantContacts.EmailContact> collect);
}
