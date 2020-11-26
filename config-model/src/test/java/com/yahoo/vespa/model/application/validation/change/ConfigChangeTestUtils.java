// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation.change;

import com.yahoo.config.application.api.ValidationId;
import com.yahoo.config.model.api.ConfigChangeAction;
import com.yahoo.config.model.api.ServiceInfo;
import com.yahoo.config.provision.ClusterSpec;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

public class ConfigChangeTestUtils {

    public static VespaConfigChangeAction newRestartAction(ClusterSpec.Id id, String message) {
        return new VespaRestartAction(id, message);
    }

    public static VespaConfigChangeAction newRestartAction(ClusterSpec.Id id, String message, List<ServiceInfo> services) {
        return new VespaRestartAction(id, message, services);
    }

    public static VespaConfigChangeAction newRefeedAction(ClusterSpec.Id id, ValidationId validationId, String message) {
        return VespaRefeedAction.of(id, validationId, message);
    }

    public static VespaConfigChangeAction newRefeedAction(ClusterSpec.Id id, ValidationId validationId, String message,
                                                          List<ServiceInfo> services, String documentType) {
        return VespaRefeedAction.of(id, validationId, message, services, documentType);
    }

    public static VespaConfigChangeAction newReindexAction(ClusterSpec.Id id, ValidationId validationId, String message) {
        return VespaReindexAction.of(id, validationId, message);
    }

    public static VespaConfigChangeAction newReindexAction(ClusterSpec.Id id, ValidationId validationId, String message,
                                                           List<ServiceInfo> services, String documentType) {
        return VespaReindexAction.of(id, validationId, message, services, documentType);
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
        var mutableExp = new ArrayList<>(exp);
        var mutableAct = new ArrayList<>(act);
        mutableExp.sort((lhs, rhs) -> lhs.getMessage().compareTo(rhs.getMessage()));
        mutableAct.sort((lhs, rhs) -> lhs.getMessage().compareTo(rhs.getMessage()));
        assertThat(mutableAct, equalTo(mutableExp));
    }

}
