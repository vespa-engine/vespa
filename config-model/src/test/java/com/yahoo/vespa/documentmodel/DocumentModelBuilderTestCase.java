// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.documentmodel;

import com.yahoo.document.DocumenttypesConfig;
import com.yahoo.document.config.DocumentmanagerConfig;
import com.yahoo.searchdefinition.SearchBuilder;
import com.yahoo.searchdefinition.SearchDefinitionTestCase;
import com.yahoo.searchdefinition.parser.ParseException;
import com.yahoo.vespa.configmodel.producers.DocumentManager;
import com.yahoo.vespa.configmodel.producers.DocumentTypes;
import org.junit.Test;
import java.io.IOException;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class DocumentModelBuilderTestCase extends SearchDefinitionTestCase {

    @Test
    public void testDocumentManagerSimple()  throws IOException, ParseException {
        DocumentModel model = createAndTestModel("src/test/configmodel/types/types.sd");

        DocumentmanagerConfig.Builder documentmanagerCfg = new DocumentManager().produce(model, new DocumentmanagerConfig.Builder());
        assertConfigFile("src/test/configmodel/types/documentmanager.cfg",
                new DocumentmanagerConfig(documentmanagerCfg).toString());
    }
    @Test
    // This is ignored as enums in config are not testable in this way. See bug 4748050
    public void testDocumentTypesSimple()  throws IOException, ParseException {
        DocumentModel model = createAndTestModel("src/test/configmodel/types/types.sd");

        DocumenttypesConfig.Builder documenttypesCfg = new DocumentTypes().produce(model, new DocumenttypesConfig.Builder());
        assertConfigFile("src/test/configmodel/types/documenttypes.cfg",
                new DocumenttypesConfig(documenttypesCfg).toString());
    }

    @Test
    public void testDocumentTypesWithDocumentField()  throws IOException, ParseException {
        SearchBuilder search = new SearchBuilder();
        search.importFile("src/test/configmodel/types/other_doc.sd");
        search.importFile("src/test/configmodel/types/type_with_doc_field.sd");
        search.build();
        DocumentModel model = search.getModel();

        DocumenttypesConfig.Builder documenttypesCfg = new DocumentTypes().produce(model, new DocumenttypesConfig.Builder());
        assertConfigFile("src/test/configmodel/types/documenttypes_with_doc_field.cfg",
                new DocumenttypesConfig(documenttypesCfg).toString());
    }

    @Test
    public void testMultipleInheritanceArray() throws IOException, ParseException {
        SearchBuilder search = new SearchBuilder();
        search.importFile("src/test/cfg/search/data/travel/searchdefinitions/TTData.sd");
        search.importFile("src/test/cfg/search/data/travel/searchdefinitions/TTEdge.sd");
        search.importFile("src/test/cfg/search/data/travel/searchdefinitions/TTPOI.sd");
        search.build();
    }

    private DocumentModel createAndTestModel(String sd) throws IOException, ParseException {
        SearchBuilder search = SearchBuilder.createFromFile(sd);
        DocumentModel model = search.getModel();

        assertEquals(2, model.getDocumentManager().getTypes().size());
        assertNotNull(model.getDocumentManager().getDocumentType("document"));
        assertNotNull(model.getDocumentManager().getDocumentType("types"));
        return model;
    }

}
