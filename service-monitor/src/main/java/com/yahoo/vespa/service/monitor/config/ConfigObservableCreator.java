// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.monitor.config;

import com.yahoo.config.ConfigInstance;
import com.yahoo.config.subscription.ConfigHandle;
import com.yahoo.config.subscription.ConfigSource;
import com.yahoo.config.subscription.ConfigSubscriber;
import com.yahoo.log.LogLevel;
import rx.Observable;

import java.util.logging.Logger;

/**
 * @author oyving
 */
public class ConfigObservableCreator {
    private static final Logger log = Logger.getLogger(ConfigObservableCreator.class.getName());

    private ConfigObservableCreator() {}

    public static <T extends ConfigInstance> Observable<T> create(
            ConfigSource configSource,
            Class<T> configClass,
            String configId) {

        return Observable.create(
            subscriber -> {
                try {
                    final ConfigSubscriber configSubscriber = new ConfigSubscriber(configSource);

                    try {
                        final ConfigHandle<T> configHandle = configSubscriber.subscribe(configClass, configId);

                        log.log(LogLevel.DEBUG, "Subscribing to configuration " + configClass + "@" + configId + " from " + configSource);

                        while (!subscriber.isUnsubscribed() && !configSubscriber.isClosed()) {
                            if (configSubscriber.nextGeneration(1000) && configHandle.isChanged()) {
                                log.log(LogLevel.DEBUG, "Received new configuration: " + configHandle);
                                T configuration = configHandle.getConfig();
                                log.log(LogLevel.DEBUG, "Received new configuration: " + configuration);
                                subscriber.onNext(configuration);
                            } else {
                                log.log(LogLevel.DEBUG, "Configuration tick with no change: " + configHandle);
                            }
                        }
                    } finally {
                        configSubscriber.close();
                    }

                    if (!subscriber.isUnsubscribed()) {
                        subscriber.onCompleted();
                    }
                } catch (Exception e) {
                    if (!subscriber.isUnsubscribed()) {
                        subscriber.onError(e);
                    }
                }
            }
        );
    }
}
