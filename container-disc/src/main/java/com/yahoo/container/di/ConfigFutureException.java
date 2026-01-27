/*
 * Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
 */

package com.yahoo.container.di;

import com.yahoo.config.ConfigInstance;
import com.yahoo.vespa.config.ConfigKey;

/**
 * @author gjoranv
 */
public class ConfigFutureException extends RuntimeException {

    public ConfigFutureException(ConfigKey<ConfigInstance> key, Throwable cause) {
        super("Failed subscribing to config " + key, cause);
    }

}
