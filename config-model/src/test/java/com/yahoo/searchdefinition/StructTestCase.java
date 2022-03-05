// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition;

import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.document.config.DocumenttypesConfig;
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
public class StructTestCase extends AbstractSchemaTestCase {

    @Test
    public void testStruct() throws IOException {
        assertConfigFile("src/test/examples/structresult.cfg",
                         new DocumentmanagerConfig(Deriver.getDocumentManagerConfig("src/test/examples/struct.sd")).toString() + "\n");
    }

    @Test
    public void testBadStruct() throws IOException {
        try {
            ApplicationBuilder.buildFromFile("src/test/examples/badstruct.sd");
            fail("Should throw exception.");
        } catch (IllegalArgumentException|ParseException expected) {
            System.err.println("As expected, with message: "+expected.getMessage());
            // success
        }
    }

    @Test
    public void testStructAndDocumentWithSameNames() {
        try {
            DocumenttypesConfig.Builder dt = Deriver.getDocumentTypesConfig("src/test/examples/structanddocumentwithsamenames.sd");
        } catch (Exception e) {
            fail("Should not have thrown exception " + e);
        }
    }

    /**
     * Declaring a struct before a document will fail, no doc type to add it to. 
     */
    @Test(expected = IllegalArgumentException.class)
    public void testStructOutsideDocumentIllegal() throws IOException, ParseException {
        var builder = new ApplicationBuilder(new TestProperties().setExperimentalSdParsing(false));
        builder.addSchemaFile("src/test/examples/structoutsideofdocument.sd");
    }

    /**
     * Declaring a struct before a document should work
     */
    @Test
    public void testStructOutsideDocumentLegal() throws IOException, ParseException {
        var builder = new ApplicationBuilder(new TestProperties().setExperimentalSdParsing(true));
        builder.addSchemaFile("src/test/examples/structoutsideofdocument.sd");
    }

}

