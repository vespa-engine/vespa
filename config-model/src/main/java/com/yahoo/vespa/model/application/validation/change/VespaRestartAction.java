// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation.change;

import com.yahoo.config.model.api.ConfigChangeRestartAction;
import com.yahoo.config.model.api.ServiceInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents an action to restart services in order to handle a config change.
 *
 * @author geirst
 * @since 5.43
 */
public class VespaRestartAction extends VespaConfigChangeAction implements ConfigChangeRestartAction {

    public VespaRestartAction(String message) {
        super(message, new ArrayList<>());
    }

    public VespaRestartAction(String message, ServiceInfo service) {
        super(message, Collections.singletonList(service));
    }

    public VespaRestartAction(String message, List<ServiceInfo> services) {
        super(message, services);
    }

    @Override
    public VespaConfigChangeAction modifyAction(String newMessage, List<ServiceInfo> newServices, String documentType) {
        return new VespaRestartAction(newMessage, newServices);
    }

}
