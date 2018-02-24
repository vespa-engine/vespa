// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.processing;

import com.yahoo.searchdefinition.SearchBuilder;
import com.yahoo.searchdefinition.parser.ParseException;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.fail;

/**
 * @author hmusum
 */
public class SearchMustHaveDocumentTest {

    @Test
    public void requireErrorWhenMissingDocument() throws IOException, ParseException {
        try {
            SearchBuilder.buildFromFile("src/test/examples/invalid_sd_missing_document.sd");
            fail("SD without document");
        } catch (IllegalArgumentException e) {
            if (!e.getMessage()
                  .contains("For search 'imageconfig': A search specification must have an equally named document inside of it.")) {
                throw e;
            }
        }
    }

}
