// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition;

import com.yahoo.searchdefinition.parser.ParseException;
import org.junit.Test;

import java.io.IOException;

/**
 * tests importing of document containing array type fields
 *
 * @author bratseth
 */
public class MultipleSummariesTestCase extends SearchDefinitionTestCase {
    @Test
    public void testArrayImporting() throws IOException, ParseException {
        SearchBuilder.buildFromFile("src/test/examples/multiplesummaries.sd");
    }
}
