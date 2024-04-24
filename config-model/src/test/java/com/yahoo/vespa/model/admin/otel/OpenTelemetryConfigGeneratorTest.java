// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.admin.otel;

import com.yahoo.config.model.ApplicationConfigProducerRoot.StatePortInfo;
import com.yahoo.config.model.producer.TreeConfigProducer;
import com.yahoo.config.model.test.MockRoot;
import com.yahoo.vespa.model.AbstractService;
import com.yahoo.vespa.model.PortAllocBridge;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author arnej
 */
public class OpenTelemetryConfigGeneratorTest {

    @Test
    void testBuildsYaml() {
        var generator = new OpenTelemetryConfigGenerator(null, null);
        var root = new MockRoot();
        var mockPort1 = new StatePortInfo("localhost", 19098,
                                          new MockService(root, "sentinel"));
        var mockSvc2 = new MockService(root, "searchnode");
        mockSvc2.setProp("clustername", "mycluster");
        mockSvc2.setProp("clustertype", "mockup");
        var mockPort2 = new StatePortInfo("other.host.local", 19102, mockSvc2);
        generator.addStatePorts(List.of(mockPort1, mockPort2));
        String yaml = generator.generate();
        // System.err.println(">>>\n" + yaml + "\n<<<");
        assertTrue(yaml.contains("sentinel"));
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

}
