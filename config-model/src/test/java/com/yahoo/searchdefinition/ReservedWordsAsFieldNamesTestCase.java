// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition;

import com.yahoo.searchdefinition.parser.ParseException;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertNotNull;

/**
 * @author bratseth
 */
public class ReservedWordsAsFieldNamesTestCase extends SearchDefinitionTestCase {

    @Test
    public void testIt() throws IOException, ParseException {
        Search search = SearchBuilder.buildFromFile("src/test/examples/reserved_words_as_field_names.sd");
        assertNotNull(search.getDocument().getField("inline"));
        assertNotNull(search.getDocument().getField("constants"));
        assertNotNull(search.getDocument().getField("reference"));
    }

}
