// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.di.config;

import com.google.inject.ProvidedBy;
import com.yahoo.container.di.CloudSubscriberFactory;
import com.yahoo.vespa.config.ConfigKey;

import java.util.Set;

/**
 * @author Tony Vaagenes
 * @author gjoranv
 */
@ProvidedBy(CloudSubscriberFactory.Provider.class)
public interface SubscriberFactory {

    Subscriber getSubscriber(Set<? extends ConfigKey<?>> configKeys, String name);
    void reloadActiveSubscribers(long generation);
    void close();
}
