// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation.change;

import com.yahoo.config.model.api.ServiceInfo;
import com.yahoo.config.application.api.ValidationOverrides;

import java.time.Instant;
import java.util.List;

public class ConfigChangeTestUtils {

    public static VespaConfigChangeAction newRestartAction(String message) {
        return new VespaRestartAction(message);
    }

    public static VespaConfigChangeAction newRestartAction(String message, List<ServiceInfo> services) {
        return new VespaRestartAction(message, services);
    }

    public static VespaConfigChangeAction newRefeedAction(String name, ValidationOverrides overrides, String message, Instant now) {
        return VespaRefeedAction.of(name, overrides, message, now);
    }

    public static VespaConfigChangeAction newRefeedAction(String name, ValidationOverrides overrides, String message,
                                                          List<ServiceInfo> services, String documentType, Instant now) {
        return VespaRefeedAction.of(name, overrides, message, services, documentType, now);
    }
}
