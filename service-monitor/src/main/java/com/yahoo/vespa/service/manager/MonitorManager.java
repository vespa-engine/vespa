// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.manager;

import com.yahoo.vespa.service.monitor.DuperModelListener;
import com.yahoo.vespa.service.monitor.ServiceStatusProvider;

/**
 * @author hakonhall
 */
public interface MonitorManager extends DuperModelListener, ServiceStatusProvider {
}
