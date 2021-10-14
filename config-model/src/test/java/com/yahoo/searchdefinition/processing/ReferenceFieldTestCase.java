// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.processing;

import com.yahoo.document.DataType;
import com.yahoo.document.Field;
import com.yahoo.document.ReferenceDataType;
import com.yahoo.searchdefinition.DocumentGraphValidator;
import com.yahoo.searchdefinition.Search;
import com.yahoo.searchdefinition.SearchBuilder;
import com.yahoo.searchdefinition.document.SDDocumentType;
import com.yahoo.searchdefinition.parser.ParseException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import static org.hamcrest.Matchers.instanceOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

/**
 * @author bjorncs
 */
public class ReferenceFieldTestCase {

    @Rule
    public final ExpectedException exceptionRule = ExpectedException.none();

    @Test
    public void reference_fields_are_parsed_from_search_definition() throws ParseException {
        SearchBuilder builder = new SearchBuilder();
        String campaignSdContent =
                "search campaign {\n" +
                "  document campaign {\n" +
                "  }\n" +
                "}";
        String salespersonSdContent =
                "search salesperson {\n" +
                "  document salesperson {\n" +
                "  }\n" +
                "}";
        String adSdContent =
                "search ad {\n" +
                "  document ad {\n" +
                "    field campaign_ref type reference<campaign> { indexing: attribute }\n" +
                "    field salesperson_ref type reference<salesperson> { indexing: attribute }\n" +
                "  }\n" +
                "}";
        builder.importString(campaignSdContent);
        builder.importString(salespersonSdContent);
        builder.importString(adSdContent);
        builder.build();
        Search search = builder.getSearch("ad");
        assertSearchContainsReferenceField("campaign_ref", "campaign", search.getDocument());
        assertSearchContainsReferenceField("salesperson_ref", "salesperson", search.getDocument());
    }

    @Test
    public void cyclic_document_dependencies_are_detected() throws ParseException {
        SearchBuilder builder = new SearchBuilder();
        String campaignSdContent =
                "search campaign {\n" +
                        "  document campaign {\n" +
                        "    field ad_ref type reference<ad> { indexing: attribute }\n" +
                        "  }\n" +
                        "}";
        String adSdContent =
                "search ad {\n" +
                        "  document ad {\n" +
                        "    field campaign_ref type reference<campaign> { indexing: attribute }\n" +
                        "  }\n" +
                        "}";
        builder.importString(campaignSdContent);
        builder.importString(adSdContent);
        exceptionRule.expect(DocumentGraphValidator.DocumentGraphException.class);
        exceptionRule.expectMessage("Document dependency cycle detected: campaign->ad->campaign.");
        builder.build();
    }

    private static void assertSearchContainsReferenceField(String expectedFieldname,
                                                           String referencedDocType,
                                                           SDDocumentType documentType) {
        Field field = documentType.getDocumentType().getField(expectedFieldname);
        assertNotNull("Field does not exist in document type: " + expectedFieldname, field);
        DataType dataType = field.getDataType();
        assertThat(dataType, instanceOf(ReferenceDataType.class));
        ReferenceDataType refField = (ReferenceDataType) dataType;
        assertEquals(referencedDocType, refField.getTargetType().getName());
    }
}
