// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.derived;

import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.config.model.application.provider.MockFileRegistry;
import com.yahoo.schema.ApplicationBuilder;
import org.junit.jupiter.api.Test;

/**
 * Tests deriving a configuration with structs in multiple .sd files
 *
 * @author arnej
 */
public class MultiStructTestCase extends AbstractExportingTestCase {

    @Test
    void testDocTypeConfigs() throws Exception {
        var logger = new TestableDeployLogger();
        var props = new TestProperties();
        ApplicationBuilder builder = ApplicationBuilder.createFromDirectory
                ("src/test/derived/multi_struct/", new MockFileRegistry(), logger, props);
        derive("multi_struct", builder, builder.getSchema("shop"));
        assertCorrectConfigFiles("multi_struct");
    }

}

