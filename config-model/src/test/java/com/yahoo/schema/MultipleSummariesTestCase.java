// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema;

import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.schema.parser.ParseException;
import org.junit.jupiter.api.Test;

import java.io.IOException;

/**
 * tests importing of document containing array type fields
 *
 * @author bratseth
 */
public class MultipleSummariesTestCase extends AbstractSchemaTestCase {

    @Test
    void testArrayImporting() throws IOException, ParseException {
        var builder = new ApplicationBuilder(new TestProperties());
        builder.addSchemaFile("src/test/examples/multiplesummaries.sd");
        builder.build(true);
    }

}
