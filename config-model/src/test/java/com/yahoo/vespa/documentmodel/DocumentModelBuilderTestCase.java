// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.documentmodel;

import com.yahoo.document.config.DocumenttypesConfig;
import com.yahoo.document.config.DocumentmanagerConfig;
import com.yahoo.schema.ApplicationBuilder;
import com.yahoo.schema.AbstractSchemaTestCase;
import com.yahoo.schema.parser.ParseException;
import com.yahoo.vespa.configmodel.producers.DocumentManager;
import com.yahoo.vespa.configmodel.producers.DocumentTypes;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class DocumentModelBuilderTestCase extends AbstractSchemaTestCase {

    @Test
    void testDocumentManagerSimple()  throws IOException, ParseException {
        DocumentModel model = createAndTestModel("src/test/configmodel/types/types.sd");

        DocumentmanagerConfig.Builder documentmanagerCfg = new DocumentManager().produce(model, new DocumentmanagerConfig.Builder());
        assertConfigFile("src/test/configmodel/types/documentmanager.cfg",
                new DocumentmanagerConfig(documentmanagerCfg).toString());
    }

    // This is ignored as enums in config are not testable in this way. See bug 4748050
    @Test
    void testDocumentTypesSimple()  throws IOException, ParseException {
        DocumentModel model = createAndTestModel("src/test/configmodel/types/types.sd");

        DocumenttypesConfig.Builder documenttypesCfg = new DocumentTypes().produce(model, new DocumenttypesConfig.Builder());
        assertConfigFile("src/test/configmodel/types/documenttypes.cfg",
                new DocumenttypesConfig(documenttypesCfg).toString());
    }

    @Test
    void testDocumentTypesWithDocumentField()  throws IOException, ParseException {
        ApplicationBuilder search = new ApplicationBuilder();
        search.addSchemaFile("src/test/configmodel/types/other_doc.sd");
        search.addSchemaFile("src/test/configmodel/types/type_with_doc_field.sd");
        search.build(true);
        DocumentModel model = search.getModel();

        DocumenttypesConfig.Builder documenttypesCfg = new DocumentTypes().produce(model, new DocumenttypesConfig.Builder());
        assertConfigFile("src/test/configmodel/types/documenttypes_with_doc_field.cfg",
                new DocumenttypesConfig(documenttypesCfg).toString());
    }

    @Test
    void testMultipleInheritanceArray() throws IOException, ParseException {
        ApplicationBuilder search = new ApplicationBuilder();
        search.addSchemaFile("src/test/cfg/search/data/travel/schemas/TTData.sd");
        search.addSchemaFile("src/test/cfg/search/data/travel/schemas/TTEdge.sd");
        search.addSchemaFile("src/test/cfg/search/data/travel/schemas/TTPOI.sd");
        search.build(true);
    }

    private DocumentModel createAndTestModel(String sd) throws IOException, ParseException {
        ApplicationBuilder search = ApplicationBuilder.createFromFile(sd);
        DocumentModel model = search.getModel();

        assertEquals(2, model.getDocumentManager().getTypes().size());
        assertNotNull(model.getDocumentManager().getDocumentType("document"));
        assertNotNull(model.getDocumentManager().getDocumentType("types"));
        return model;
    }

}
