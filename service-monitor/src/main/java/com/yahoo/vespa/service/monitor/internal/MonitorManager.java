// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.monitor.internal;// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

import com.yahoo.config.model.api.SuperModelListener;
import com.yahoo.vespa.service.monitor.ServiceStatusProvider;

/**
 * @author hakon
 */
public interface MonitorManager extends SuperModelListener, ServiceStatusProvider {
}
