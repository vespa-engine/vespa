// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.derived;

import com.yahoo.searchdefinition.SchemaBuilder;
import com.yahoo.searchdefinition.parser.ParseException;
import org.junit.Test;

import java.io.IOException;

/**
 * Test structs for streaming with another unrelated .sd present
 *
 * @author arnej27959
 */
public class TwoStreamingStructsTestCase extends AbstractExportingTestCase {
    @Test
    public void testTwoStreamingStructsExporting() throws ParseException, IOException {

        String root = "src/test/derived/twostreamingstructs";
        SchemaBuilder builder = new SchemaBuilder();
        builder.importFile(root + "/streamingstruct.sd");
        builder.importFile(root + "/whatever.sd");
        builder.build();
        assertCorrectDeriving(builder, builder.getSchema("streamingstruct"), root);

        builder = new SchemaBuilder();
        builder.importFile(root + "/streamingstruct.sd");
        builder.importFile(root + "/whatever.sd");
        builder.build();
        assertCorrectDeriving(builder, builder.getSchema("streamingstruct"), root);
    }
}
