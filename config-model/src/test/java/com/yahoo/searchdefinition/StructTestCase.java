// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition;

import com.yahoo.document.DocumenttypesConfig;
import com.yahoo.document.config.DocumentmanagerConfig;
import com.yahoo.searchdefinition.derived.Deriver;
import com.yahoo.searchdefinition.parser.ParseException;
import org.junit.Test;
import java.io.IOException;
import static org.junit.Assert.fail;

/**
 * Tests importing of document containing array type fields
 *
 * @author bratseth
 */
public class StructTestCase extends SearchDefinitionTestCase {

    @Test
    public void testStruct() throws IOException {
        assertConfigFile("src/test/examples/structresult.cfg",
                         new DocumentmanagerConfig(Deriver.getDocumentManagerConfig("src/test/examples/struct.sd")).toString() + "\n");
    }

    @Test
    public void testBadStruct() throws IOException {
        try {
            SearchBuilder.buildFromFile("src/test/examples/badstruct.sd");
            fail("Should throw exception.");
        } catch (ParseException expected) {
            // success
        }
    }

    @Test
    public void testStructAndDocumentWithSameNames() {
        try {
            DocumenttypesConfig.Builder dt = Deriver.getDocumentTypesConfig("src/test/examples/structanddocumentwithsamenames.sd");
        } catch (Exception e) {
            fail("Should not have thrown exception " + e.toString());
        }
    }

    /**
     * Declaring a struct before a document will fail, no doc type to add it to. 
     */
    @Test(expected = IllegalArgumentException.class)
    public void testStructOutsideDocumentIllegal() throws IOException, ParseException {
        SearchBuilder.buildFromFile("src/test/examples/structoutsideofdocument.sd");
    }

}

