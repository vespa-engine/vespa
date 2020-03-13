// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition;

import com.yahoo.searchdefinition.document.SDField;
import com.yahoo.searchdefinition.parser.ParseException;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;

/**
 * Tests comment handling
 *
 * @author bratseth
 */
public class CommentTestCase extends SearchDefinitionTestCase {

    @Test
    public void testComments() throws IOException, ParseException {
        Search search = SearchBuilder.buildFromFile("src/test/examples/comment.sd");
        SDField field = search.getConcreteField("a");
        assertEquals("{ input a | tokenize normalize stem:\"BEST\" | summary a | index a; }",
                     field.getIndexingScript().toString());
    }

}
