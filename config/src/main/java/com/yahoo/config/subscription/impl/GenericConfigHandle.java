// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.subscription.impl;

import com.yahoo.config.subscription.ConfigHandle;
import com.yahoo.vespa.config.RawConfig;

/**
 * A config handle which does not use the config class, but payload instead. Used in config proxy.
 *
 * @author vegardh
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public class GenericConfigHandle extends ConfigHandle {

    private final GenericJRTConfigSubscription subscription;

    public GenericConfigHandle(GenericJRTConfigSubscription subscription) {
        super(subscription);
        this.subscription = subscription;
    }

    public RawConfig getRawConfig() {
        return subscription.getRawConfig();
    }

}
