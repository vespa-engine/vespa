// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.derived;

import com.yahoo.config.model.application.provider.MockFileRegistry;
import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.io.IOUtils;
import com.yahoo.schema.ApplicationBuilder;
import com.yahoo.schema.parser.ParseException;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;

/**
 * @author bratseth
 */
public class SchemaInheritanceTestCase extends AbstractExportingTestCase {

    @Test
    void testIt() throws IOException, ParseException {
        try {
            ApplicationBuilder builder = ApplicationBuilder.createFromDirectory("src/test/derived/schemainheritance/",
                    new MockFileRegistry(),
                    new TestableDeployLogger(),
                    new TestProperties());
            derive("schemainheritance", builder, builder.getSchema("child"));
            assertCorrectConfigFiles("schemainheritance");
        }
        finally {
            IOUtils.recursiveDeleteDir(new File("src/test/derived/schemainheritance/models.generated/"));
        }
    }

}
