// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.processing;

import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.document.DataType;
import com.yahoo.document.Field;
import com.yahoo.documentmodel.NewDocumentReferenceDataType;
import com.yahoo.schema.Schema;
import com.yahoo.schema.ApplicationBuilder;
import com.yahoo.schema.document.SDDocumentType;
import com.yahoo.schema.parser.ParseException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author bjorncs
 */
public class ReferenceFieldTestCase {

    @Test
    void reference_fields_are_parsed_from_search_definition() throws ParseException {
        ApplicationBuilder builder = new ApplicationBuilder();
        String campaignSdContent =
                "schema campaign {\n" +
                        "  document campaign {\n" +
                        "  }\n" +
                        "}";
        String salespersonSdContent =
                "schema salesperson {\n" +
                        "  document salesperson {\n" +
                        "  }\n" +
                        "}";
        String adSdContent =
                "schema ad {\n" +
                        "  document ad {\n" +
                        "    field campaign_ref type reference<campaign> { indexing: attribute }\n" +
                        "    field salesperson_ref type reference<salesperson> { indexing: attribute }\n" +
                        "  }\n" +
                        "}";
        builder.addSchema(campaignSdContent);
        builder.addSchema(salespersonSdContent);
        builder.addSchema(adSdContent);
        builder.build(true);
        Schema schema = builder.getSchema("ad");
        assertSearchContainsReferenceField("campaign_ref", "campaign", schema.getDocument());
        assertSearchContainsReferenceField("salesperson_ref", "salesperson", schema.getDocument());
    }

    @Test
    void cyclic_document_dependencies_are_detected() throws ParseException {
        Throwable exception = assertThrows(IllegalArgumentException.class, () -> {
            var builder = new ApplicationBuilder(new TestProperties());
            String campaignSdContent =
                    "schema campaign {\n" +
                            "  document campaign {\n" +
                            "    field ad_ref type reference<ad> { indexing: attribute }\n" +
                            "  }\n" +
                            "}";
            String adSdContent =
                    "schema ad {\n" +
                            "  document ad {\n" +
                            "    field campaign_ref type reference<campaign> { indexing: attribute }\n" +
                            "  }\n" +
                            "}";
            builder.addSchema(campaignSdContent);
            builder.addSchema(adSdContent);
            builder.build(true);
        });
        assertTrue(exception.getMessage().contains("reference cycle for documents"));
    }

    private static void assertSearchContainsReferenceField(String expectedFieldname,
                                                           String referencedDocType,
                                                           SDDocumentType documentType) {
        Field field = documentType.getDocumentType().getField(expectedFieldname);
        assertNotNull(field, "Field does not exist in document type: " + expectedFieldname);
        DataType dataType = field.getDataType();
        assertTrue(dataType instanceof NewDocumentReferenceDataType);
        NewDocumentReferenceDataType refField = (NewDocumentReferenceDataType) dataType;
        assertEquals(referencedDocType, refField.getTargetTypeName());
        assertFalse(refField.isTemporary());
    }

}
