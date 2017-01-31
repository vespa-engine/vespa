package com.yahoo.vespa.documentmodel;

import com.yahoo.document.config.DocumentmanagerConfig;
import com.yahoo.searchdefinition.SearchBuilder;
import com.yahoo.searchdefinition.SearchDefinitionTestCase;
import com.yahoo.searchdefinition.parser.ParseException;
import com.yahoo.vespa.configmodel.producers.DocumentManager;
import org.junit.Test;

import java.io.IOException;

import static com.yahoo.searchdefinition.TestUtils.joinLines;

/**
 * @author geirst
 */
public class DocumentModelBuilderReferenceTypeTestCase extends SearchDefinitionTestCase {

    @Test
    public void reference_fields_can_reference_other_document_types() throws ParseException, IOException {
        assertDocumentConfigs(new TestDocumentModelBuilder().addCampaign().addPerson().build(joinLines(
                "search ad {",
                "  document ad {",
                "    field campaign_ref type reference<campaign> {}",
                "    field person_ref type reference<person> {}",
                "  }",
                "}")),
                "documentmanager_refs_to_other_types.cfg");
    }

    @Test
    public void reference_fields_can_reference_same_document_type_multiple_times() throws ParseException, IOException {
        assertDocumentConfigs(new TestDocumentModelBuilder().addCampaign().build(joinLines(
                "search ad {",
                "  document ad {",
                "    field campaign_ref type reference<campaign> {}",
                "    field other_campaign_ref type reference<campaign> {}",
                "  }",
                "}")),
                "documentmanager_refs_to_same_type.cfg");
    }

    @Test
    public void reference_field_can_reference_self_document_type() throws ParseException, IOException {
        assertDocumentConfigs(new TestDocumentModelBuilder().build(joinLines(
                "search ad {",
                "  document ad {",
                "    field self_ref type reference<ad> {}",
                "  }",
                "}")),
                "documentmanager_ref_to_self_type.cfg");
    }

    private static String TEST_FOLDER = "src/test/configmodel/types/references/";

    private void assertDocumentConfigs(DocumentModel model,
                                       String documentmanagerCfgFile) throws IOException {
        assertDocumentmanagerCfg(model, documentmanagerCfgFile);
    }

    private void assertDocumentmanagerCfg(DocumentModel model, String documentmanagerCfgFile) throws IOException {
        DocumentmanagerConfig.Builder documentmanagerCfg = new DocumentManager().produce(model, new DocumentmanagerConfig.Builder());
        assertConfigFile(TEST_FOLDER + documentmanagerCfgFile, new DocumentmanagerConfig(documentmanagerCfg).toString());
    }

    private static class TestDocumentModelBuilder {
        private final SearchBuilder builder = new SearchBuilder();
        public TestDocumentModelBuilder addCampaign() throws ParseException {
            builder.importString(joinLines("search campaign {",
                    "  document campaign {}",
                    "}"));
            return this;
        }
        public TestDocumentModelBuilder addPerson() throws ParseException {
            builder.importString(joinLines("search person {",
                    "  document person {}",
                    "}"));
            return this;
        }
        public DocumentModel build(String adSdContent) throws ParseException {
            builder.importString(adSdContent);
            builder.build();
            return builder.getModel();
        }
    }

}
