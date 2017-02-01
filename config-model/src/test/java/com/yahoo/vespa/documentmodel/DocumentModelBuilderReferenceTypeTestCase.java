package com.yahoo.vespa.documentmodel;

import com.yahoo.document.DocumenttypesConfig;
import com.yahoo.document.ReferenceDataType;
import com.yahoo.document.config.DocumentmanagerConfig;
import com.yahoo.documentmodel.NewDocumentType;
import com.yahoo.searchdefinition.SearchBuilder;
import com.yahoo.searchdefinition.SearchDefinitionTestCase;
import com.yahoo.searchdefinition.parser.ParseException;
import com.yahoo.vespa.configmodel.producers.DocumentManager;
import com.yahoo.vespa.configmodel.producers.DocumentTypes;
import org.junit.Test;

import java.io.IOException;

import static com.yahoo.searchdefinition.TestUtils.joinLines;
import static junit.framework.TestCase.assertSame;

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
                "refs_to_other_types");
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
                "refs_to_same_type");
    }

    @Test
    public void reference_field_can_reference_self_document_type() throws ParseException, IOException {
        assertDocumentConfigs(new TestDocumentModelBuilder().build(joinLines(
                "search ad {",
                "  document ad {",
                "    field self_ref type reference<ad> {}",
                "  }",
                "}")),
                "ref_to_self_type");
    }

    @Test
    public void reference_data_type_has_a_concrete_target_type() throws ParseException {
        DocumentModel model = new TestDocumentModelBuilder().addCampaign().build(joinLines(
                "search ad {",
                "  document ad {",
                "    field campaign_ref type reference<campaign> {}",
                "  }",
                "}"));
        NewDocumentType campaignType = model.getDocumentManager().getDocumentType("campaign");
        NewDocumentType adType = model.getDocumentManager().getDocumentType("ad");
        ReferenceDataType campaignRefType = (ReferenceDataType) adType.getField("campaign_ref").getDataType();
        assertSame(campaignRefType.getTargetType(), campaignType);
    }

    private static String TEST_FOLDER = "src/test/configmodel/types/references/";

    private void assertDocumentConfigs(DocumentModel model,
                                       String cfgFileSpec) throws IOException {
        assertDocumentmanagerCfg(model, "documentmanager_" + cfgFileSpec + ".cfg");
        assertDocumenttypesCfg(model , "documenttypes_" + cfgFileSpec + ".cfg");
    }

    private void assertDocumentmanagerCfg(DocumentModel model, String documentmanagerCfgFile) throws IOException {
        DocumentmanagerConfig.Builder documentmanagerCfg = new DocumentManager().produce(model, new DocumentmanagerConfig.Builder());
        assertConfigFile(TEST_FOLDER + documentmanagerCfgFile, new DocumentmanagerConfig(documentmanagerCfg).toString());
    }

    private void assertDocumenttypesCfg(DocumentModel model, String documenttypesCfgFile) throws IOException {
        DocumenttypesConfig.Builder documenttypesCfg = new DocumentTypes().produce(model, new DocumenttypesConfig.Builder());
        assertConfigFile(TEST_FOLDER + documenttypesCfgFile, new DocumenttypesConfig(documenttypesCfg).toString());
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
