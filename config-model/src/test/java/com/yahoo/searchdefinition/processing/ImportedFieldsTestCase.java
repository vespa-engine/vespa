// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.processing;

import com.yahoo.searchdefinition.Search;
import com.yahoo.searchdefinition.SearchBuilder;
import com.yahoo.searchdefinition.document.ImportedComplexField;
import com.yahoo.searchdefinition.document.ImportedField;
import com.yahoo.searchdefinition.parser.ParseException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import static org.junit.Assert.assertEquals;
import static com.yahoo.config.model.test.TestUtil.joinLines;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * @author geirst
 */
public class ImportedFieldsTestCase {

    @Test
    public void fields_can_be_imported_from_referenced_document_types() throws ParseException {
        Search search = buildAdSearch(joinLines(
                "search ad {",
                "  document ad {",
                "    field campaign_ref type reference<campaign> { indexing: attribute }",
                "    field person_ref type reference<person> { indexing: attribute }",
                "  }",
                "  import field campaign_ref.budget as my_budget {}",
                "  import field person_ref.name as my_name {}",
                "}"));
        assertEquals(2, search.importedFields().get().fields().size());
        assertSearchContainsImportedField("my_budget", "campaign_ref", "campaign", "budget", search);
        assertSearchContainsImportedField("my_name", "person_ref", "person", "name", search);
    }

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Test
    public void field_reference_spec_must_include_dot() throws ParseException {
        exception.expect(IllegalArgumentException.class);
        exception.expectMessage("Illegal field reference spec 'campaignrefbudget': Does not include a single '.'");
        buildAdSearch(joinLines(
                "search ad {",
                "  document ad {}",
                "  import field campaignrefbudget as budget {}",
                "}"));
    }

    @Test
    public void fail_duplicate_import() throws ParseException {
        exception.expect(IllegalArgumentException.class);
        exception.expectMessage("For search 'ad', import field as 'my_budget': Field already imported");
        Search search = buildAdSearch(joinLines(
                "search ad {",
                "  document ad {",
                "    field campaign_ref type reference<campaign> { indexing: attribute }",
                "  }",
                "  import field campaign_ref.budget as my_budget {}",
                "  import field campaign_ref.budget as my_budget {}",
                "}"));
    }

    private static Search buildAdSearch(String sdContent) throws ParseException {
        SearchBuilder builder = new SearchBuilder();
        builder.importString(joinLines("search campaign {",
                "  document campaign {",
                "    field budget type int { indexing: attribute }",
                "  }",
                "}"));
        builder.importString(joinLines("search person {",
                "  document person {",
                "    field name type string { indexing: attribute }",
                "  }",
                "}"));
        builder.importString(sdContent);
        builder.build();
        return builder.getSearch("ad");
    }

    private static void checkStructImport(ParentSdBuilder parentBuilder) throws ParseException {
        Search search = buildChildSearch(parentBuilder.build(), joinLines("search child {",
                "  document child {",
                "    field parent_ref type reference<parent> {",
                "      indexing: attribute | summary",
                "    }",
                "  }",
                "  import field parent_ref.elem_array as my_elem_array {}",
                "  import field parent_ref.elem_map as my_elem_map {}",
                "  import field parent_ref.str_int_map as my_str_int_map {}",
                "}"));
        assertEquals(3, search.importedFields().get().fields().size());
        checkImportedField("my_elem_array.name", "parent_ref", "parent", "elem_array.name", search, parentBuilder.elem_array_name_attr);
        checkImportedField("my_elem_array.weight", "parent_ref", "parent", "elem_array.weight", search, parentBuilder.elem_array_weight_attr);
        checkImportedField("my_elem_map.key", "parent_ref", "parent", "elem_map.key", search, parentBuilder.elem_map_key_attr);
        checkImportedField("my_elem_map.value.name", "parent_ref", "parent", "elem_map.value.name", search, parentBuilder.elem_map_value_name_attr);
        checkImportedField("my_elem_map.value.weight", "parent_ref", "parent", "elem_map.value.weight", search, parentBuilder.elem_map_value_weight_attr);
        checkImportedField("my_str_int_map.key", "parent_ref", "parent", "str_int_map.key", search, parentBuilder.str_int_map_key_attr);
        checkImportedField("my_str_int_map.value", "parent_ref", "parent", "str_int_map.value", search, parentBuilder.str_int_map_value_attr);
        checkImportedField("my_elem_array", "parent_ref", "parent", "elem_array", search, true);
        checkImportedField("my_elem_map", "parent_ref", "parent", "elem_map", search, true);
        checkImportedField("my_str_int_map", "parent_ref", "parent", "str_int_map", search, true);
    }

    @Test
    public void check_struct_import() throws ParseException {
        checkStructImport(new ParentSdBuilder());
        checkStructImport(new ParentSdBuilder().elem_array_weight_attr(false).elem_map_value_weight_attr(false));
        checkStructImport(new ParentSdBuilder().elem_array_name_attr(false).elem_map_value_name_attr(false));
    }

    @Test
    public void check_illegal_struct_import_missing_array_of_struct_attributes() throws ParseException {
        exception.expect(IllegalArgumentException.class);
        exception.expectMessage("For search 'child', import field 'my_elem_array': Field 'elem_array' via reference field 'parent_ref': Is not a struct containing an attribute field.");
        checkStructImport(new ParentSdBuilder().elem_array_name_attr(false).elem_array_weight_attr(false));
    }

    @Test
    public void check_illegal_struct_import_missing_map_of_struct_key_attribute() throws ParseException {
        exception.expect(IllegalArgumentException.class);
        exception.expectMessage("For search 'child', import field 'my_elem_map' (nested to 'my_elem_map.key'): Field 'elem_map.key' via reference field 'parent_ref': Is not an attribute field. Only attribute fields supported");
        checkStructImport(new ParentSdBuilder().elem_map_key_attr(false));
    }

    @Test
    public void check_illegal_struct_import_missing_map_of_struct_value_attributes() throws ParseException {
        exception.expect(IllegalArgumentException.class);
        exception.expectMessage("For search 'child', import field 'my_elem_map' (nested to 'my_elem_map.value'): Field 'elem_map.value' via reference field 'parent_ref': Is not a struct containing an attribute field.");
        checkStructImport(new ParentSdBuilder().elem_map_value_name_attr(false).elem_map_value_weight_attr(false));
    }

    @Test
    public void check_illegal_struct_import_missing_map_of_primitive_key_attribute() throws ParseException {
        exception.expect(IllegalArgumentException.class);
        exception.expectMessage("For search 'child', import field 'my_str_int_map' (nested to 'my_str_int_map.key'): Field 'str_int_map.key' via reference field 'parent_ref': Is not an attribute field. Only attribute fields supported");
        checkStructImport(new ParentSdBuilder().str_int_map_key_attr(false));
    }

    @Test
    public void check_illegal_struct_import_missing_map_of_primitive_value_attribute() throws ParseException {
        exception.expect(IllegalArgumentException.class);
        exception.expectMessage("For search 'child', import field 'my_str_int_map' (nested to 'my_str_int_map.value'): Field 'str_int_map.value' via reference field 'parent_ref': Is not an attribute field. Only attribute fields supported");
        checkStructImport(new ParentSdBuilder().str_int_map_value_attr(false));
    }

    private static class ParentSdBuilder {
        private boolean elem_array_name_attr;
        private boolean elem_array_weight_attr;
        private boolean elem_map_key_attr;
        private boolean elem_map_value_name_attr;
        private boolean elem_map_value_weight_attr;
        private boolean str_int_map_key_attr;
        private boolean str_int_map_value_attr;

        public ParentSdBuilder() {
            elem_array_name_attr = true;
            elem_array_weight_attr = true;
            elem_map_key_attr = true;
            elem_map_value_name_attr = true;
            elem_map_value_weight_attr = true;
            str_int_map_key_attr = true;
            str_int_map_value_attr = true;
        }

        public ParentSdBuilder elem_array_name_attr(boolean v) { elem_array_name_attr = v; return this; }
        public ParentSdBuilder elem_array_weight_attr(boolean v) { elem_array_weight_attr = v; return this; }
        public ParentSdBuilder elem_map_key_attr(boolean v) { elem_map_key_attr = v; return this; }
        public ParentSdBuilder elem_map_value_name_attr(boolean v) { elem_map_value_name_attr = v; return this; }
        public ParentSdBuilder elem_map_value_weight_attr(boolean v) { elem_map_value_weight_attr = v; return this; }
        public ParentSdBuilder str_int_map_key_attr(boolean v) { str_int_map_key_attr = v; return this; }
        public ParentSdBuilder str_int_map_value_attr(boolean v) { str_int_map_value_attr = v; return this; }

        public String build() {
            return joinLines("search parent {",
                    "  document parent {",
                    "    struct elem {",
                    "      field name type string {}",
                    "      field weight type int {}",
                    "    }",
                    "    field elem_array type array<elem> {",
                    "      indexing: summary",
                    "      struct-field name {",
                    structFieldSpec(elem_array_name_attr),
                    "      }",
                    "      struct-field weight {",
                    structFieldSpec(elem_array_weight_attr),
                    "      }",
                    "    }",
                    "    field elem_map type map<string, elem> {",
                    "      indexing: summary",
                    "      struct-field key {",
                    structFieldSpec(elem_map_key_attr),
                    "      }",
                    "      struct-field value.name {",
                    structFieldSpec(elem_map_value_name_attr),
                    "      }",
                    "      struct-field value.weight {",
                    structFieldSpec(elem_map_value_weight_attr),
                    "      }",
                    "    }",
                    "    field str_int_map type map<string, int> {",
                    "      indexing: summary",
                    "      struct-field key {",
                    structFieldSpec(str_int_map_key_attr),
                    "      }",
                    "      struct-field value {",
                    structFieldSpec(str_int_map_value_attr),
                    "      }",
                    "    }",
                    "  }",
                    "}");
        }

        private static String structFieldSpec(boolean isAttribute) {
            return isAttribute ? "        indexing: attribute" : "";
        }

        private static int b2i(boolean b) {
            return b ? 1 : 0;
        }

        public int countAttrs() {
            int elem_array_attr_count = b2i(elem_array_name_attr) + b2i(elem_array_weight_attr);
            int elem_map_attr_count = b2i(elem_map_key_attr) + b2i(elem_map_value_name_attr) + b2i(elem_map_value_weight_attr);
            int str_int_map_attr_count = b2i(str_int_map_key_attr) + b2i(str_int_map_value_attr);
            return elem_array_attr_count + elem_map_attr_count + str_int_map_attr_count;
        }
    }

    private static Search buildChildSearch(String parentSdContent, String sdContent) throws ParseException {
        SearchBuilder builder = new SearchBuilder();
        builder.importString(parentSdContent);
        builder.importString(sdContent);
        builder.build();
        return builder.getSearch("child");
    }

    private static class ParentPosSdBuilder {
        public String build() {
            return joinLines("search parent {",
                    "  document parent {",
                    "field pos type position {",
                    "indexing: attribute | summary",
                    "    }",
                    "  }",
                    "}");
        }
    }

    private static class ChildPosSdBuilder {
        private boolean import_pos_zcurve_before;

        public ChildPosSdBuilder() {
            import_pos_zcurve_before = false;
        }

        ChildPosSdBuilder import_pos_zcurve_before(boolean v) { import_pos_zcurve_before = v; return this; }

        public String build() {
            return joinLines("search child {",
                    "  document child {",
                    "    field parent_ref type reference<parent> {",
                    "      indexing: attribute | summary",
                    "    }",
                    "  }",
                    importPosZCurve(import_pos_zcurve_before),
                    "  import field parent_ref.pos as my_pos {}",
                    "}");
        }

        private static String importPosZCurve(boolean doImport) {
            return doImport ? "import field parent_ref.pos_zcurve as my_pos_zcurve {}" : "";
        }
    }

    private static void checkPosImport(ParentPosSdBuilder parentBuilder, ChildPosSdBuilder childBuilder) throws ParseException {
        Search search = buildChildSearch(parentBuilder.build(), childBuilder.build());
        assertEquals(2, search.importedFields().get().fields().size());
        assertSearchContainsImportedField("my_pos_zcurve", "parent_ref", "parent", "pos_zcurve", search);
        assertSearchContainsImportedField("my_pos", "parent_ref", "parent", "pos", search);
    }

    @Test
    public void check_pos_import() throws ParseException {
        checkPosImport(new ParentPosSdBuilder(), new ChildPosSdBuilder());
    }

    @Test
    public void check_pos_import_after_pos_zcurve_import() throws ParseException {
        exception.expect(IllegalArgumentException.class);
        exception.expectMessage("For search 'child', import field 'my_pos_zcurve': Field 'pos_zcurve' via reference field 'parent_ref': Field already imported");
        checkPosImport(new ParentPosSdBuilder(), new ChildPosSdBuilder().import_pos_zcurve_before(true));
    }

    private static ImportedField getImportedField(String name, Search search) {
        if (name.contains(".")) {
            assertNull(search.importedFields().get().fields().get(name));
            String superFieldName = name.substring(0,name.indexOf("."));
            String subFieldName = name.substring(name.indexOf(".")+1);
            ImportedField superField = search.importedFields().get().fields().get(superFieldName);
            if (superField != null && superField instanceof ImportedComplexField) {
                return ((ImportedComplexField)superField).getNestedField(subFieldName);
            }
            return null;
        }
        return search.importedFields().get().fields().get(name);
    }

    private static void assertSearchNotContainsImportedField(String fieldName, Search search) {
        ImportedField importedField = getImportedField(fieldName, search);
        assertNull(importedField);
    }

    private static void assertSearchContainsImportedField(String fieldName,
                                                          String referenceFieldName,
                                                          String referenceDocType,
                                                          String targetFieldName,
                                                          Search search) {
        ImportedField importedField = getImportedField(fieldName, search);
        assertNotNull(importedField);
        assertEquals(fieldName, importedField.fieldName());
        assertEquals(referenceFieldName, importedField.reference().referenceField().getName());
        assertEquals(referenceDocType, importedField.reference().targetSearch().getName());
        assertEquals(targetFieldName, importedField.targetField().getName());
    }

    private static void checkImportedField(String fieldName, String referenceFieldName, String referenceDocType,
                                           String targetFieldName, Search search, boolean present) {
        if (present) {
            assertSearchContainsImportedField(fieldName, referenceFieldName, referenceDocType, targetFieldName, search);
        } else {
            assertSearchNotContainsImportedField(fieldName, search);
        }
    }
}
