package com.yahoo.vespa.documentmodel;

import com.yahoo.document.DocumenttypesConfig;
import com.yahoo.document.config.DocumentmanagerConfig;
import com.yahoo.searchdefinition.SearchDefinitionTestCase;
import com.yahoo.vespa.configmodel.producers.DocumentManager;
import com.yahoo.vespa.configmodel.producers.DocumentTypes;

import java.io.IOException;

/**
 * Utility functions for testing generated configs for reference/imported fields.
 */
public abstract class AbstractReferenceFieldTestCase extends SearchDefinitionTestCase {

    private static String TEST_FOLDER = "src/test/configmodel/types/references/";

    protected void assertDocumentConfigs(DocumentModel model,
                                         String cfgFileSpec) throws IOException {
        assertDocumentmanagerCfg(model, "documentmanager_" + cfgFileSpec + ".cfg");
        assertDocumenttypesCfg(model , "documenttypes_" + cfgFileSpec + ".cfg");
    }

    protected void assertDocumentmanagerCfg(DocumentModel model, String documentmanagerCfgFile) throws IOException {
        DocumentmanagerConfig.Builder documentmanagerCfg = new DocumentManager().produce(model, new DocumentmanagerConfig.Builder());
        assertConfigFile(TEST_FOLDER + documentmanagerCfgFile, new DocumentmanagerConfig(documentmanagerCfg).toString());
    }

    protected void assertDocumenttypesCfg(DocumentModel model, String documenttypesCfgFile) throws IOException {
        DocumenttypesConfig.Builder documenttypesCfg = new DocumentTypes().produce(model, new DocumenttypesConfig.Builder());
        assertConfigFile(TEST_FOLDER + documenttypesCfgFile, new DocumenttypesConfig(documenttypesCfg).toString());
    }

}
