// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.subscription;

import com.yahoo.config.ConfigInstance;
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.config.ConfigKey;

import java.util.logging.Logger;

// Debug class that provides useful helper routines
public class ConfigDebug {
    public static void logDebug(Logger logger, long timestamp, ConfigKey<?> key, String logmessage) {
        if (key.getConfigId().matches(".*container.?\\d+.*") || key.getConfigId().matches(".*doc.api.*")) {
            logger.log(LogLevel.INFO,  timestamp + " " + key + " " + logmessage);
        }
    }

    public static void logDebug(Logger log, ConfigInstance.Builder builder, String configId, String logmessage) {
        ConfigKey<?> key = new ConfigKey<>(builder.getDefName(), configId, builder.getDefNamespace());
        logDebug(log, 0, key, logmessage);
    }
}
