// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.processing;

import com.yahoo.searchdefinition.Search;
import com.yahoo.searchdefinition.SearchDefinitionTestCase;
import com.yahoo.searchdefinition.UnprocessingSearchBuilder;
import com.yahoo.searchdefinition.parser.ParseException;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
/**
 *  Test AttributeIndex processor.
 *
 * @author hmusum
 */
public class AttributeIndexTestCase extends SearchDefinitionTestCase {
    @Test
    public void testAttributeIndex() throws IOException, ParseException {
        Search search = UnprocessingSearchBuilder.buildUnprocessedFromFile("src/test/examples/attributeindex.sd");

        assertTrue(search.getConcreteField("nosettings").getAttributes().get("nosettings") != null);

        assertTrue(search.getConcreteField("specifyname").getAttributes().get("newname") != null);

        assertTrue(search.getConcreteField("specifyname2").getAttributes().get("newname2") != null);

        assertTrue(search.getConcreteField("withstaticrankname").getAttributes().get("withstaticrankname") != null);

        assertTrue(search.getConcreteField("withstaticrankname").getAttributes().get("someothername") != null);
    }
}
