// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.derived;

import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.config.model.application.provider.MockFileRegistry;
import com.yahoo.schema.ApplicationBuilder;
import org.junit.jupiter.api.Test;

/**
 * Tests deriving a configuration with references from multiple .sd files
 *
 * @author arnej
 */
public class ReferenceFromSeveralTestCase extends AbstractExportingTestCase {

    @Test
    void testDocManConfigs() throws Exception {
        var logger = new TestableDeployLogger();
        var props = new TestProperties();
        ApplicationBuilder builder = ApplicationBuilder.createFromDirectory
                ("src/test/derived/reference_from_several/", new MockFileRegistry(), logger, props);
        derive("reference_from_several", builder, builder.getSchema("foo"));
        assertCorrectConfigFiles("reference_from_several");
    }

}

