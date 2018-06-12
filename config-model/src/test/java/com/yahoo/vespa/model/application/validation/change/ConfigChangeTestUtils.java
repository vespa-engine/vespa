// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation.change;

import com.yahoo.config.model.api.ConfigChangeAction;
import com.yahoo.config.model.api.ServiceInfo;
import com.yahoo.config.application.api.ValidationOverrides;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

public class ConfigChangeTestUtils {

    public static VespaConfigChangeAction newRestartAction(String message) {
        return new VespaRestartAction(message);
    }

    public static VespaConfigChangeAction newRestartAction(String message, List<ServiceInfo> services) {
        return new VespaRestartAction(message, services);
    }

    public static VespaConfigChangeAction newRefeedAction(String name, String message) {
        return VespaRefeedAction.of(name, ValidationOverrides.empty, message, Instant.now());
    }

    public static VespaConfigChangeAction newRefeedAction(String name, ValidationOverrides overrides, String message, Instant now) {
        return VespaRefeedAction.of(name, overrides, message, now);
    }

    public static VespaConfigChangeAction newRefeedAction(String name, ValidationOverrides overrides, String message,
                                                          List<ServiceInfo> services, String documentType, Instant now) {
        return VespaRefeedAction.of(name, overrides, message, services, documentType, now);
    }

    public static List<ConfigChangeAction> normalizeServicesInActions(List<ConfigChangeAction> result) {
        return result.stream()
                .map(action -> ((VespaConfigChangeAction) action).modifyAction(
                        action.getMessage(),
                        normalizeServices(action.getServices()),
                        action.getType().equals(ConfigChangeAction.Type.REFEED) ?
                                ((VespaRefeedAction)action).getDocumentType() : ""))
                .collect(Collectors.toList());
    }

    public static List<ServiceInfo> normalizeServices(List<ServiceInfo> services) {
        return services.stream()
                .map(service -> new ServiceInfo(service.getServiceName(), "null", null, null,
                        service.getConfigId(), "null"))
                .collect(Collectors.toList());
    }

    public static void assertEqualActions(List<ConfigChangeAction> exp, List<ConfigChangeAction> act) {
        exp.sort((lhs, rhs) -> lhs.getMessage().compareTo(rhs.getMessage()));
        act.sort((lhs, rhs) -> lhs.getMessage().compareTo(rhs.getMessage()));
        assertThat(act, equalTo(exp));
    }

}
