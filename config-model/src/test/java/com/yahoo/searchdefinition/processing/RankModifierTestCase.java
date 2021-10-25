// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.processing;

import com.yahoo.searchdefinition.Schema;
import com.yahoo.searchdefinition.SchemaBuilder;
import com.yahoo.searchdefinition.AbstractSchemaTestCase;
import com.yahoo.searchdefinition.parser.ParseException;
import org.junit.Test;

import java.io.IOException;

/**
 * Tests for the field "rank {" shortcut
 * @author vegardh
 *
 */
public class RankModifierTestCase extends AbstractSchemaTestCase {
    @Test
    public void testLiteral() throws IOException, ParseException {
        Schema schema = SchemaBuilder.buildFromFile("src/test/examples/rankmodifier/literal.sd");
    }
}
