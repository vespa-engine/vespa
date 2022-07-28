// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.derived;

import com.yahoo.schema.ApplicationBuilder;
import com.yahoo.schema.parser.ParseException;
import org.junit.jupiter.api.Test;

import java.io.IOException;

/**
 * Test structs for streaming with another unrelated .sd present
 *
 * @author arnej27959
 */
public class TwoStreamingStructsTestCase extends AbstractExportingTestCase {

    @Test
    void testTwoStreamingStructsExporting() throws ParseException, IOException {

        String root = "src/test/derived/twostreamingstructs";
        ApplicationBuilder builder = new ApplicationBuilder();
        builder.addSchemaFile(root + "/streamingstruct.sd");
        builder.addSchemaFile(root + "/whatever.sd");
        builder.build(true);
        assertCorrectDeriving(builder, builder.getSchema("streamingstruct"), root);

        builder = new ApplicationBuilder();
        builder.addSchemaFile(root + "/streamingstruct.sd");
        builder.addSchemaFile(root + "/whatever.sd");
        builder.build(true);
        assertCorrectDeriving(builder, builder.getSchema("streamingstruct"), root);
    }

}
