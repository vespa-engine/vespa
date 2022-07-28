
// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema;

import com.yahoo.document.config.DocumenttypesConfig;
import com.yahoo.document.config.DocumentmanagerConfig;
import com.yahoo.schema.derived.Deriver;
import com.yahoo.schema.parser.ParseException;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Tests importing of document containing array type fields
 *
 * @author bratseth
 */
public class StructTestCase extends AbstractSchemaTestCase {

    @Test
    void testStruct() throws IOException {
        assertConfigFile("src/test/examples/structresult.cfg",
                new DocumentmanagerConfig(Deriver.getDocumentManagerConfig("src/test/examples/struct.sd")) + "\n");
    }

    @Test
    void testBadStruct() throws IOException {
        try {
            ApplicationBuilder.buildFromFile("src/test/examples/badstruct.sd");
            fail("Should throw exception.");
        } catch (IllegalArgumentException | ParseException expected) {
            System.err.println("As expected, with message: " + expected.getMessage());
            // success
        }
    }

    @Test
    @Disabled
    void testStructAndDocumentWithSameNames() {
        try {
            DocumenttypesConfig.Builder dt = Deriver.getDocumentTypesConfig("src/test/examples/structanddocumentwithsamenames.sd");
            // while the above line may work, the config generated will fail.
            // See also NameCollisionTestCase.
        } catch (Exception e) {
            fail("Should not have thrown exception " + e);
        }
    }

    /**
     * Declaring a struct before a document should work
     */
    @Test
    void testStructOutsideDocumentLegal() throws IOException, ParseException {
        new ApplicationBuilder().addSchemaFile("src/test/examples/structoutsideofdocument.sd");
    }

}

