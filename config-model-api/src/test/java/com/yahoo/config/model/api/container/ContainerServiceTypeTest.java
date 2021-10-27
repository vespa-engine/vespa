// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.api.container;

import org.junit.Test;

import static com.yahoo.config.model.api.container.ContainerServiceType.CLUSTERCONTROLLER_CONTAINER;
import static com.yahoo.config.model.api.container.ContainerServiceType.CONTAINER;
import static com.yahoo.config.model.api.container.ContainerServiceType.LOGSERVER_CONTAINER;
import static com.yahoo.config.model.api.container.ContainerServiceType.METRICS_PROXY_CONTAINER;
import static com.yahoo.config.model.api.container.ContainerServiceType.QRSERVER;
import static org.junit.Assert.assertEquals;

/**
 * @author gjoranv
 */
public class ContainerServiceTypeTest {

    @Test
    public void new_values_are_not_added_without_updating_tests() {
        assertEquals(5, ContainerServiceType.values().length);
    }

    @Test
    public void service_names_do_not_change() {
        assertEquals("container", CONTAINER.serviceName);
        assertEquals("qrserver", QRSERVER.serviceName);
        assertEquals("container-clustercontroller", CLUSTERCONTROLLER_CONTAINER.serviceName);
        assertEquals("logserver-container", LOGSERVER_CONTAINER.serviceName);
        assertEquals("metricsproxy-container", METRICS_PROXY_CONTAINER.serviceName);
    }

}
