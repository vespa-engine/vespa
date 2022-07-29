// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.processing;

import com.yahoo.schema.ApplicationBuilder;
import com.yahoo.schema.parser.ParseException;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author hmusum
 */
public class SchemaMustHaveDocumentTest {

    @Test
    void requireErrorWhenMissingDocument() throws IOException, ParseException {
        try {
            ApplicationBuilder.buildFromFile("src/test/examples/invalid_sd_missing_document.sd");
            fail("SD without document");
        } catch (IllegalArgumentException e) {
            if (!e.getMessage()
                    .contains("For schema 'imageconfig': A search specification must have an equally named document inside of it.")) {
                throw e;
            }
        }
    }

}
