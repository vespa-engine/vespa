/*
* Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
 */

package ai.vespa.metricsproxy.service;

import java.util.List;

/**
 * @author Unknown
 */
public interface ServiceListener {
    void setServices(List<VespaService> services);
}
