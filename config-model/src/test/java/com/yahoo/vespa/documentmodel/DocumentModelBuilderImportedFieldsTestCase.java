// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.documentmodel;

import com.yahoo.schema.ApplicationBuilder;
import com.yahoo.schema.parser.ParseException;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static com.yahoo.config.model.test.TestUtil.joinLines;

public class DocumentModelBuilderImportedFieldsTestCase extends AbstractReferenceFieldTestCase {

    @Test
    void imported_fields_are_included_in_generated_document_configs() throws ParseException, IOException {
        assertDocumentConfigs(new TestDocumentModelBuilder().addCampaign().addPerson().build(joinLines(
                        "search ad {",
                        "  document ad {",
                        "    field campaign_ref type reference<campaign> { indexing: attribute }",
                        "    field person_ref type reference<person> { indexing: attribute }",
                        "  }",
                        "  import field campaign_ref.cool_field as my_cool_field {}",
                        "  import field campaign_ref.swag_field as my_swag_field {}",
                        "  import field person_ref.name as my_name {}",
                        "}")),
                "multiple_imported_fields");
    }

    private static class TestDocumentModelBuilder {
        private final ApplicationBuilder builder = new ApplicationBuilder();
        public TestDocumentModelBuilder addCampaign() throws ParseException {
            builder.addSchema(joinLines("search campaign {",
                                        "  document campaign {",
                                        "    field cool_field type string { indexing: attribute }",
                                        "    field swag_field type long { indexing: attribute }",
                                        "  }",
                                        "}"));
            return this;
        }
        public TestDocumentModelBuilder addPerson() throws ParseException {
            builder.addSchema(joinLines("search person {",
                                        "  document person {",
                                        "    field name type string { indexing: attribute }",
                                        "  }",
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
