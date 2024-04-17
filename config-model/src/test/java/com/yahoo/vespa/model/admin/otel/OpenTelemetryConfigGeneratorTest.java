// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.admin.otel;

import com.yahoo.config.model.ApplicationConfigProducerRoot.StatePortInfo;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author arnej
 */
public class OpenTelemetryConfigGeneratorTest {

    @Test
    void testBuildsYaml() {
        var generator = new OpenTelemetryConfigGenerator(null);
        generator.addStatePorts(List.of(new StatePortInfo("localhost", 19098, "config-sentinel", "sentinel")));
        String yaml = generator.generate();
        assertTrue(yaml.contains("sentinel"));
    }

}
