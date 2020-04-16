// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.processing;

import com.yahoo.searchdefinition.Search;
import com.yahoo.searchdefinition.SearchBuilder;
import com.yahoo.searchdefinition.SchemaTestCase;
import com.yahoo.searchdefinition.parser.ParseException;
import org.junit.Test;

import java.io.IOException;

/**
 * Tests for the field "rank {" shortcut
 * @author vegardh
 *
 */
public class RankModifierTestCase extends SchemaTestCase {
    @Test
    public void testLiteral() throws IOException, ParseException {
        Search search = SearchBuilder.buildFromFile("src/test/examples/rankmodifier/literal.sd");
    }
}
