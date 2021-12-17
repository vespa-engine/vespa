// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.service;

import com.google.common.collect.ImmutableList;
import org.junit.Test;

import java.util.List;

import static ai.vespa.metricsproxy.service.VespaServices.ALL_SERVICES;
import static org.junit.Assert.assertEquals;

/**
 * TODO: add more tests
 *
 * @author gjoranv
 */
public class VespaServicesTest {

    @Test
    public void services_can_be_retrieved_from_monitoring_name() {
        List<VespaService> dummyServices = ImmutableList.of(
                new DummyService(0, "dummy/id/0"),
                new DummyService(1, "dummy/id/1"));
        VespaServices services = new VespaServices(dummyServices);

        assertEquals(2, services.getMonitoringServices("vespa.dummy").size());
    }

    @Test
    public void all_services_can_be_retrieved_by_using_special_name() {
        List<VespaService> dummyServices = ImmutableList.of(
                new DummyService(0, "dummy/id/0"));
        VespaServices services = new VespaServices(dummyServices);

        assertEquals(1, services.getMonitoringServices(ALL_SERVICES).size());
    }

}
