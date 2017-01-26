package com.yahoo.searchdefinition.processing;

import com.yahoo.document.DataType;
import com.yahoo.document.Field;
import com.yahoo.document.ReferenceDataType;
import com.yahoo.searchdefinition.Search;
import com.yahoo.searchdefinition.SearchBuilder;
import com.yahoo.searchdefinition.document.SDDocumentType;
import com.yahoo.searchdefinition.parser.ParseException;
import org.junit.Test;

import static org.hamcrest.Matchers.instanceOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

/**
 * @author bjorncs
 */
public class ReferenceFieldTestCase {

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
                "    field campaign_ref type reference<campaign> {}\n" +
                "    field salesperson_ref type reference<salesperson> {}\n" +
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
