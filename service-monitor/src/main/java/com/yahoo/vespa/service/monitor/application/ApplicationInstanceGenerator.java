// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.monitor.application;

import com.yahoo.vespa.applicationmodel.ApplicationInstance;
import com.yahoo.vespa.service.monitor.ServiceStatusProvider;

/**
 * @author hakon
 */
public interface ApplicationInstanceGenerator {
    /** Make an ApplicationInstance based on current service status. */
    ApplicationInstance makeApplicationInstance(ServiceStatusProvider serviceStatusProvider);
}
