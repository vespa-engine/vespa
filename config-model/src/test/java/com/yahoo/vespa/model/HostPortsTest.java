// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model;

import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.config.model.test.MockRoot;
import com.yahoo.config.provision.NetworkPorts;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * @author arnej
 */
public class HostPortsTest {

    @Test
    void next_available_baseport_is_BASE_PORT_when_no_ports_have_been_reserved() {
        HostPorts host = new HostPorts("myhostname");
        assertThat(host.nextAvailableBaseport(1), is(HostPorts.BASE_PORT));
    }

    @Test
    void next_available_baseport_is_BASE_PORT_plus_one_when_one_port_has_been_reserved() {
        HostPorts host = new HostPorts("myhostname");
        MockRoot root = new MockRoot();
        host.reservePort(new TestService(root, 1), HostPorts.BASE_PORT, "foo");
        assertThat(host.nextAvailableBaseport(1), is(HostPorts.BASE_PORT + 1));
    }

    @Test
    void no_available_baseport_when_service_requires_more_consecutive_ports_than_available() {
        HostPorts host = new HostPorts("myhostname");
        MockRoot root = new MockRoot();

        for (int p = HostPorts.BASE_PORT; p < HostPorts.BASE_PORT + HostPorts.MAX_PORTS; p += 2) {
            host.reservePort(new TestService(root, 1), p, "foo");
        }
        assertThat(host.nextAvailableBaseport(2), is(0));

        try {
            host.reservePort(new TestService(root, 2), HostPorts.BASE_PORT, "bar");
        } catch (RuntimeException e) {
            assertThat(e.getMessage(), containsString("Too many ports are reserved"));
        }
    }

    @Test
    void port_above_vespas_port_range_can_be_reserved() {
        HostPorts host = new HostPorts("myhostname");
        MockRoot root = new MockRoot();
        host.allocatePorts(new TestService(root, 1), HostPorts.BASE_PORT + HostPorts.MAX_PORTS + 1);
    }

    @Test
    void allocating_same_port_throws_exception() {
        assertThrows(RuntimeException.class, () -> {
            HostPorts host = new HostPorts("myhostname");
            MockRoot root = new MockRoot();
            TestService service1 = new TestService(root, 1);
            TestService service2 = new TestService(root, 1);

            host.allocatePorts(service1, HostPorts.BASE_PORT);
            host.allocatePorts(service2, HostPorts.BASE_PORT);
        });
    }

    @Test
    void allocating_overlapping_ports_throws_exception() {
        assertThrows(RuntimeException.class, () -> {
            HostPorts host = new HostPorts("myhostname");
            MockRoot root = new MockRoot();
            TestService service2 = new TestService(root, 2);
            TestService service1 = new TestService(root, 1);

            host.allocatePorts(service2, HostPorts.BASE_PORT);
            host.allocatePorts(service1, HostPorts.BASE_PORT + 1);
        });
    }

    NetworkPorts emulOldPorts() {
        List<NetworkPorts.Allocation> list = new ArrayList<>();
        list.add(new NetworkPorts.Allocation(8080, "qrs", "foo", "http"));
        list.add(new NetworkPorts.Allocation(19101, "slobrok", "slobrok.0", "http"));
        return new NetworkPorts(list);
    }

    @Test
    void use_old_port_when_available() {
        HostPorts host = new HostPorts("myhostname");
        host.addNetworkPorts(emulOldPorts());

        MockRoot root = new MockRoot();
        Service service = new MockSlobrok(root, 0);
        assertThat(service.getConfigId(), is("slobrok.0"));

        // check that matching service get port from saved allocations
        List<Integer> ports = host.allocatePorts(service, 0);
        assertThat(ports.size(), is(1));
        assertThat(ports.get(0), is(19101));

        // check that new service get next free port
        ports = host.allocatePorts(new MockSlobrok(root, 1), 0);
        assertThat(ports.get(0), is(19100));

        // check that new service get next free port, skipping the allocated one
        ports = host.allocatePorts(new MockSlobrok(root, 2), 0);
        assertThat(ports.get(0), is(19102));
    }

    private static class MockSlobrok extends AbstractService {
        MockSlobrok(AbstractConfigProducer parent, int number) {
            super(parent, "slobrok."+number);
        }
        @Override public int getPortCount() { return 1; }
        @Override public String getServiceType() { return "slobrok"; }

        @Override
        public void allocatePorts(int start, PortAllocBridge from) {
            from.allocatePort("http");
        }
    }

    private static int counter = 0;
    int getCounter() { return ++counter; }

    private class TestService extends AbstractService {
        private final int portCount;

        TestService(AbstractConfigProducer parent, int portCount) {
            super(parent, "testService" + getCounter());
            this.portCount = portCount;
        }

        @Override
        public boolean requiresWantedPort() {
            return true;
        }

        @Override
        public int getPortCount() { return portCount; }

        @Override
        public void allocatePorts(int start, PortAllocBridge from) {
            for (int i = 0; i < portCount; i++) {
                String suffix = "generic." + i;
                if (start == 0) {
                    from.allocatePort(suffix);
                } else {
                    from.requirePort(start++, suffix);
                }
            }
        }

    }
}
