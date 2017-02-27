// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model;

import com.yahoo.config.model.test.MockRoot;
import org.junit.Test;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

/**
 * @author gjoranv
 * @since 5.1.14
 */
public class HostResourceTest {

    @Test
    public void next_available_baseport_is_BASE_PORT_when_no_ports_have_been_reserved() {
        HostResource host = mockHostResource();
        assertThat(host.nextAvailableBaseport(1), is(HostResource.BASE_PORT));
    }

    @Test
    public void next_available_baseport_is_BASE_PORT_plus_one_when_one_port_has_been_reserved() {
        HostResource host = mockHostResource();
        host.reservePort(new TestService(1), HostResource.BASE_PORT);
        assertThat(host.nextAvailableBaseport(1), is(HostResource.BASE_PORT + 1));
    }

    @Test
    public void no_available_baseport_when_service_requires_more_consecutive_ports_than_available() {
        HostResource host = mockHostResource();

        for (int p = HostResource.BASE_PORT; p < HostResource.BASE_PORT + HostResource.MAX_PORTS; p += 2) {
            host.reservePort(new TestService(1), p);
        }
        assertThat(host.nextAvailableBaseport(2), is(0));

        try {
            host.reservePort(new TestService(2), HostResource.BASE_PORT);
        } catch (RuntimeException e) {
            assertThat(e.getMessage(), containsString("Too many ports are reserved"));
        }
    }

    @Test
    public void require_exception_when_no_matching_hostalias() {
        TestService service = new TestService(1);
        try {
            service.initService();
        } catch (RuntimeException e) {
            assertThat(e.getMessage(), endsWith("No host found for service 'hostresourcetest$testservice0'. " +
                    "The hostalias is probably missing from hosts.xml."));
        }
    }

    @Test
    public void port_above_vespas_port_range_can_be_reserved() {
        HostResource host = mockHostResource();
        host.allocateService(new TestService(1), HostResource.BASE_PORT + HostResource.MAX_PORTS + 1);
    }

    @Test(expected = RuntimeException.class)
    public void allocating_same_port_throws_exception() {
        HostResource host = mockHostResource();
        TestService service1 = new TestService(1);
        TestService service2 = new TestService(1);

        host.allocateService(service1, HostResource.BASE_PORT);
        host.allocateService(service2, HostResource.BASE_PORT);
    }

    @Test(expected = RuntimeException.class)
    public void allocating_overlapping_ports_throws_exception() {
        HostResource host = mockHostResource();
        TestService service2 = new TestService(2);
        TestService service1 = new TestService(1);

        host.allocateService(service2, HostResource.BASE_PORT);
        host.allocateService(service1, HostResource.BASE_PORT + 1);
    }


    private HostResource mockHostResource() {
        return new HostResource(new Host(new MockRoot()));
    }

    private class TestService extends AbstractService {
        private final int portCount;

        TestService(int portCount) {
            super("testService");
            this.portCount = portCount;
        }

        @Override
        public boolean requiresWantedPort() {
            return true;
        }

        @Override
        public int getPortCount() { return portCount; }
    }
}
