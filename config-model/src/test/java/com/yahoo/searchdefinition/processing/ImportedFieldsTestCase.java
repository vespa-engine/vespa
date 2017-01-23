package com.yahoo.searchdefinition.processing;

import com.yahoo.searchdefinition.Search;
import com.yahoo.searchdefinition.SearchBuilder;
import com.yahoo.searchdefinition.document.TemporaryImportedField;
import com.yahoo.searchdefinition.parser.ParseException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import static org.junit.Assert.assertEquals;
import static com.yahoo.searchdefinition.TestUtils.joinLines;

/**
 * @author geirst
 */
public class ImportedFieldsTestCase {

    @Test
    public void require_that_imported_fields_can_be_parsed_from_sd_file() throws ParseException {
        Search search = build(joinLines(
                "search ad {",
                "  document ad {}",
                "  import field campaign.budget as budget {}",
                "  import field person.name as sales_person {}",
                "}"));
        assertEquals(2, search.importedFields().fields().size());
        assertSearchContainsTemporaryImportedField("budget", "campaign", "budget", search);
        assertSearchContainsTemporaryImportedField("sales_person", "person", "name", search);
    }

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Test
    public void require_that_field_reference_spec_must_include_dot() throws ParseException {
        exception.expect(IllegalArgumentException.class);
        exception.expectMessage("Illegal field reference spec 'campaignbudget': Does not include a single '.'");
        build(joinLines(
                "search ad {",
                "  document ad {}",
                "  import field campaignbudget as budget {}",
                "}"));
    }

    private static Search build(String sdContent) throws ParseException {
        SearchBuilder builder = new SearchBuilder();
        builder.importString(sdContent);
        builder.build();
        return builder.getSearch();
    }

    private static void assertSearchContainsTemporaryImportedField(String fieldName, String refFieldName, String fieldNameInRefType, Search search) {
        TemporaryImportedField importedField = search.importedFields().fields().get(fieldName);
        assertEquals(fieldName, importedField.fieldName());
        assertEquals(refFieldName, importedField.reference().refFieldName());
        assertEquals(fieldNameInRefType, importedField.reference().fieldNameInRefType());
    }
}
