package com.yahoo.searchdefinition.document;

import com.yahoo.searchdefinition.Search;
import com.yahoo.searchdefinition.SearchBuilder;
import com.yahoo.searchdefinition.parser.ParseException;
import org.junit.Test;

import static com.yahoo.config.model.test.TestUtil.joinLines;
import static com.yahoo.searchdefinition.document.ComplexAttributeFieldUtils.isArrayOfSimpleStruct;
import static com.yahoo.searchdefinition.document.ComplexAttributeFieldUtils.isComplexFieldWithOnlyStructFieldAttributes;
import static com.yahoo.searchdefinition.document.ComplexAttributeFieldUtils.isMapOfSimpleStruct;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ComplexAttributeFieldUtilsTestCase {

    private static ImmutableSDField createField(String fieldName, String sdFieldContent) throws ParseException {
        String sdContent = joinLines("search test {",
                "  document test {",
                "    struct elem {",
                "      field name type string {}",
                "      field weight type string {}",
                "    }",
                sdFieldContent,
                "  }",
                "}");
        Search search = SearchBuilder.createFromString(sdContent).getSearch();
        return search.getConcreteField(fieldName);
    }

    @Test
    public void array_of_struct_with_only_struct_field_attributes_is_tagged_as_such() throws ParseException {
        ImmutableSDField field = createField("elem_array",
                joinLines("field elem_array type array<elem> {",
                        "  indexing: summary",
                        "  struct-field name { indexing: attribute }",
                        "  struct-field weight { indexing: attribute }",
                        "}"));
        assertTrue(isArrayOfSimpleStruct(field));
        assertTrue(isComplexFieldWithOnlyStructFieldAttributes(field));
    }

    @Test
    public void array_of_struct_with_some_struct_field_attributes_is_tagged_as_such() throws ParseException {
        ImmutableSDField field = createField("elem_array",
                joinLines("field elem_array type array<elem> {",
                        "  indexing: summary",
                        "  struct-field weight { indexing: attribute }",
                        "}"));
        assertTrue(isArrayOfSimpleStruct(field));
        assertFalse(isComplexFieldWithOnlyStructFieldAttributes(field));
    }

    @Test
    public void map_of_struct_with_only_struct_field_attributes_is_tagged_as_such() throws ParseException {
        ImmutableSDField field = createField("elem_map",
                joinLines("field elem_map type map<string, elem> {",
                        "  indexing: summary",
                        "  struct-field key { indexing: attribute }",
                        "  struct-field value.name { indexing: attribute }",
                        "  struct-field value.weight { indexing: attribute }",
                        "}"));
        assertTrue(isMapOfSimpleStruct(field));
        assertTrue(isComplexFieldWithOnlyStructFieldAttributes(field));
    }

    @Test
    public void map_of_struct_with_some_struct_field_attributes_is_tagged_as_such() throws ParseException {
        {
            ImmutableSDField field = createField("elem_map",
                    joinLines("field elem_map type map<string, elem> {",
                            "  indexing: summary",
                            "  struct-field value.name { indexing: attribute }",
                            "  struct-field value.weight { indexing: attribute }",
                            "}"));
            assertTrue(isMapOfSimpleStruct(field));
            assertFalse(isComplexFieldWithOnlyStructFieldAttributes(field));
        }
        {
            ImmutableSDField field = createField("elem_map",
                    joinLines("field elem_map type map<string, elem> {",
                            "  indexing: summary",
                            "  struct-field key { indexing: attribute }",
                            "  struct-field value.weight { indexing: attribute }",
                            "}"));
            assertTrue(isMapOfSimpleStruct(field));
            assertFalse(isComplexFieldWithOnlyStructFieldAttributes(field));
        }
    }
}
