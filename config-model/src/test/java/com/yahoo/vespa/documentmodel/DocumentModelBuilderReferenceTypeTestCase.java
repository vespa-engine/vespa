// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.documentmodel;

import com.yahoo.document.ReferenceDataType;
import com.yahoo.documentmodel.NewDocumentType;
import com.yahoo.searchdefinition.SearchBuilder;
import com.yahoo.searchdefinition.parser.ParseException;
import org.junit.Test;

import java.io.IOException;

import static com.yahoo.config.model.test.TestUtil.joinLines;
import static org.junit.Assert.assertEquals;

/**
 * @author geirst
 */
public class DocumentModelBuilderReferenceTypeTestCase extends AbstractReferenceFieldTestCase {

    @Test
    public void reference_fields_can_reference_other_document_types() throws ParseException, IOException {
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
    public void reference_fields_can_reference_same_document_type_multiple_times() throws ParseException, IOException {
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
    public void reference_data_type_has_a_concrete_target_type() throws ParseException {
        DocumentModel model = new TestDocumentModelBuilder().addCampaign().build(joinLines(
                "search ad {",
                "  document ad {",
                "    field campaign_ref type reference<campaign> { indexing: attribute }",
                "  }",
                "}"));
        NewDocumentType campaignType = model.getDocumentManager().getDocumentType("campaign");
        NewDocumentType adType = model.getDocumentManager().getDocumentType("ad");
        ReferenceDataType campaignRefType = (ReferenceDataType) adType.getField("campaign_ref").getDataType();
        assertEquals(campaignRefType.getTargetType(), campaignType);
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
