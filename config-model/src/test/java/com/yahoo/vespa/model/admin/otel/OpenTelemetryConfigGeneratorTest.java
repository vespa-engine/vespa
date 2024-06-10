// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.admin.otel;

import com.yahoo.config.model.ApplicationConfigProducerRoot.StatePortInfo;
import com.yahoo.config.model.producer.TreeConfigProducer;
import com.yahoo.config.model.test.MockRoot;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterMembership;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.HostSpec;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.model.AbstractService;
import com.yahoo.vespa.model.Host;
import com.yahoo.vespa.model.HostResource;
import com.yahoo.vespa.model.AbstractService;
import com.yahoo.vespa.model.PortAllocBridge;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author arnej
 */
public class OpenTelemetryConfigGeneratorTest {

    @Test
    void testBuildsYaml() {
        var mockZone = new Zone(SystemName.PublicCd, Environment.prod, RegionName.from("mock"));
        var app = ApplicationId.from("mytenant", "myapp", "myinstance");
        var generator = new OpenTelemetryConfigGenerator(mockZone, app, true);
        var root = new MockRoot();

        var mockHost = new Host(root, "localhost2.local");
        var mockVersion = new com.yahoo.component.Version(8);
        var mockCluster = ClusterMembership.from("container/feeding/2/3", mockVersion, Optional.empty());
        var noResource = NodeResources.unspecified();
        var mockHostSpec = new HostSpec("localhost1.local",
                                        noResource, noResource, noResource,
                                        mockCluster,
                                        Optional.empty(), Optional.empty(), Optional.empty());
        var mockHostResource = new HostResource(mockHost, mockHostSpec);
        var mockSvc1 = new MockService(root, "sentinel");
        mockSvc1.setHostResource(mockHostResource);
        var mockPort1 = new StatePortInfo("localhost", 19098, mockSvc1);

        var mockSvc2 = new MockService(root, "searchnode");
        mockSvc2.setProp("clustername", "mycluster");
        mockSvc2.setProp("clustertype", "mockup");
        var mockPort2 = new StatePortInfo("other123x.host.local", 19102, mockSvc2);

        generator.addStatePorts(List.of(mockPort1, mockPort2));
        String yaml = generator.generate();
        // System.err.println(">>>\n" + yaml + "\n<<<");
        assertTrue(yaml.contains("sentinel"));
        String want = """
                      "parentHostname":"other123.host.local""";
        assertTrue(yaml.contains(want));
    }

    static class MockService extends AbstractService {
        private final String name;
        public MockService(TreeConfigProducer<?> parent, String name) {
            super(parent, name);
            this.name = name;
        }
        public String getServiceName() { return name; }
        public String getServiceType() { return "dummy"; }
        @Override public int getPortCount() { return 0; }
        @Override public void allocatePorts(int start, PortAllocBridge from) { }
    }

    @Test
    void testFindParentHost() {
        String result;
        result = OpenTelemetryConfigGenerator.findParentHost("n1234c.foo.bar.some.cloud");
        assertEquals("n1234.foo.bar.some.cloud", result);
        result = OpenTelemetryConfigGenerator.findParentHost("n1234-v6-7.foo.bar.some.cloud");
        assertEquals("n1234.foo.bar.some.cloud", result);
        result = OpenTelemetryConfigGenerator.findParentHost("2000a.foo.bar.some.cloud");
        assertEquals("2000.foo.bar.some.cloud", result);
        result = OpenTelemetryConfigGenerator.findParentHost("2000-v6-10.foo.bar.some.cloud");
        assertEquals("2000.foo.bar.some.cloud", result);
        result = OpenTelemetryConfigGenerator.findParentHost("foobar.some.cloud");
        assertNull(result);
        result = OpenTelemetryConfigGenerator.findParentHost("foo123bar.some.cloud");
        assertNull(result);
        result = OpenTelemetryConfigGenerator.findParentHost("foo123.some.cloud");
        assertNull(result);
    }
}
