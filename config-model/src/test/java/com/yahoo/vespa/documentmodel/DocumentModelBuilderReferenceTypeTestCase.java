// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.documentmodel;

import com.yahoo.documentmodel.NewDocumentReferenceDataType;
import com.yahoo.documentmodel.NewDocumentType;
import com.yahoo.schema.ApplicationBuilder;
import com.yahoo.schema.parser.ParseException;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static com.yahoo.config.model.test.TestUtil.joinLines;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author geirst
 */
public class DocumentModelBuilderReferenceTypeTestCase extends AbstractReferenceFieldTestCase {

    @Test
    void reference_fields_can_reference_other_document_types() throws ParseException, IOException {
        assertDocumentConfigs(new TestDocumentModelBuilder().addCampaign().addPerson().build(joinLines(
                        "search ad {",
                        "  document ad {",
                        "    field campaign_ref type reference<campaign> { indexing: attribute }",
                        "    field person_ref type reference<person> { indexing: attribute }",
                        "  }",
                        "}")),
                "refs_to_other_types");
    }

    @Test
    void reference_fields_can_reference_same_document_type_multiple_times() throws ParseException, IOException {
        assertDocumentConfigs(new TestDocumentModelBuilder().addCampaign().build(joinLines(
                        "search ad {",
                        "  document ad {",
                        "    field campaign_ref type reference<campaign> { indexing: attribute }",
                        "    field other_campaign_ref type reference<campaign> { indexing: attribute }",
                        "  }",
                        "}")),
                "refs_to_same_type");
    }

    @Test
    void reference_data_type_has_a_concrete_target_type() throws ParseException {
        DocumentModel model = new TestDocumentModelBuilder().addCampaign().build(joinLines(
                "search ad {",
                "  document ad {",
                "    field campaign_ref type reference<campaign> { indexing: attribute }",
                "  }",
                "}"));
        NewDocumentType campaignType = model.getDocumentManager().getDocumentType("campaign");
        NewDocumentType adType = model.getDocumentManager().getDocumentType("ad");
        NewDocumentReferenceDataType campaignRefType = (NewDocumentReferenceDataType) adType.getField("campaign_ref").getDataType();
        assertEquals(campaignRefType.getTargetType(), campaignType);
    }

    private static class TestDocumentModelBuilder {
        private final ApplicationBuilder builder = new ApplicationBuilder();
        public TestDocumentModelBuilder addCampaign() throws ParseException {
            builder.addSchema(joinLines("search campaign {",
                                        "  document campaign {}",
                                        "}"));
            return this;
        }
        public TestDocumentModelBuilder addPerson() throws ParseException {
            builder.addSchema(joinLines("search person {",
                                        "  document person {}",
                                        "}"));
            return this;
        }
        public DocumentModel build(String adSdContent) throws ParseException {
            builder.addSchema(adSdContent);
            builder.build(true);
            return builder.getModel();
        }
    }

}
