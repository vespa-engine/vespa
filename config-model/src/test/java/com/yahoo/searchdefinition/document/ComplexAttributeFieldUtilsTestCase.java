// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.document;

import com.yahoo.searchdefinition.Search;
import com.yahoo.searchdefinition.SearchBuilder;
import com.yahoo.searchdefinition.parser.ParseException;
import org.junit.Test;

import static com.yahoo.config.model.test.TestUtil.joinLines;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ComplexAttributeFieldUtilsTestCase {

    private static class FixtureBase {
        private final Search search;
        private final ImmutableSDField field;

        public FixtureBase(String fieldName, String sdContent) throws ParseException {
            search = SearchBuilder.createFromString(sdContent).getSearch();
            field = search.getConcreteField(fieldName);
        }

        public ImmutableSDField field() {
            return field;
        }

        public SDDocumentType docType() {
            return search.getDocument();
        }

        public boolean isSupportedComplexField() {
            return ComplexAttributeFieldUtils.isSupportedComplexField(field(), docType());
        }

        public boolean isArrayOfSimpleStruct() {
            return ComplexAttributeFieldUtils.isArrayOfSimpleStruct(field(), docType());
        }

        public boolean isMapOfSimpleStruct() {
            return ComplexAttributeFieldUtils.isMapOfSimpleStruct(field(), docType());
        }

        public boolean isMapOfPrimitiveType() {
            return ComplexAttributeFieldUtils.isMapOfPrimitiveType(field());
        }

        public boolean isComplexFieldWithOnlyStructFieldAttributes() {
            return ComplexAttributeFieldUtils.isComplexFieldWithOnlyStructFieldAttributes(field(), docType());
        }
    }

    private static class Fixture extends FixtureBase {

        public Fixture(String fieldName, String sdFieldContent) throws ParseException {
            super(fieldName, joinLines("search test {",
                    "  document test {",
                    "    struct elem {",
                    "      field name type string {}",
                    "      field weight type string {}",
                    "    }",
                    sdFieldContent,
                    "  }",
                    "}"));
        }
    }

    private static class ComplexFixture extends FixtureBase {

        public ComplexFixture(String fieldName, String sdFieldContent) throws ParseException {
            super(fieldName, joinLines("search test {",
                    "  document test {",
                    "    struct elem {",
                    "      field name type string {}",
                    "      field weight type array<string> {}",
                    "    }",
                    sdFieldContent,
                    "  }",
                    "}"));
        }
    }

    @Test
    public void array_of_struct_with_only_struct_field_attributes_is_tagged_as_such() throws ParseException {
        Fixture f = new Fixture("elem_array",
                joinLines("field elem_array type array<elem> {",
                        "  indexing: summary",
                        "  struct-field name { indexing: attribute }",
                        "  struct-field weight { indexing: attribute }",
                        "}"));
        assertTrue(f.isSupportedComplexField());
        assertTrue(f.isArrayOfSimpleStruct());
        assertTrue(f.isComplexFieldWithOnlyStructFieldAttributes());
    }

    @Test
    public void array_of_struct_with_some_struct_field_attributes_is_tagged_as_such() throws ParseException {
        Fixture f = new Fixture("elem_array",
                joinLines("field elem_array type array<elem> {",
                        "  indexing: summary",
                        "  struct-field weight { indexing: attribute }",
                        "}"));
        assertTrue(f.isSupportedComplexField());
        assertTrue(f.isArrayOfSimpleStruct());
        assertFalse(f.isComplexFieldWithOnlyStructFieldAttributes());
    }

    @Test
    public void map_of_struct_with_only_struct_field_attributes_is_tagged_as_such() throws ParseException {
        Fixture f = new Fixture("elem_map",
                joinLines("field elem_map type map<string, elem> {",
                        "  indexing: summary",
                        "  struct-field key { indexing: attribute }",
                        "  struct-field value.name { indexing: attribute }",
                        "  struct-field value.weight { indexing: attribute }",
                        "}"));
        assertTrue(f.isSupportedComplexField());
        assertTrue(f.isMapOfSimpleStruct());
        assertFalse(f.isMapOfPrimitiveType());
        assertTrue(f.isComplexFieldWithOnlyStructFieldAttributes());
    }

    @Test
    public void map_of_struct_with_some_struct_field_attributes_is_tagged_as_such() throws ParseException {
        {
            Fixture f = new Fixture("elem_map",
                    joinLines("field elem_map type map<int, elem> {",
                            "  indexing: summary",
                            "  struct-field value.name { indexing: attribute }",
                            "  struct-field value.weight { indexing: attribute }",
                            "}"));
            assertTrue(f.isSupportedComplexField());
            assertTrue(f.isMapOfSimpleStruct());
            assertFalse(f.isMapOfPrimitiveType());
            assertFalse(f.isComplexFieldWithOnlyStructFieldAttributes());
        }
        {
            Fixture f = new Fixture("elem_map",
                    joinLines("field elem_map type map<int, elem> {",
                            "  indexing: summary",
                            "  struct-field key { indexing: attribute }",
                            "  struct-field value.weight { indexing: attribute }",
                            "}"));
            assertTrue(f.isSupportedComplexField());
            assertTrue(f.isMapOfSimpleStruct());
            assertFalse(f.isMapOfPrimitiveType());
            assertFalse(f.isComplexFieldWithOnlyStructFieldAttributes());
        }
    }

    @Test
    public void map_of_primitive_type_with_only_struct_field_attributes_is_tagged_as_such() throws ParseException {
        Fixture f = new Fixture("str_map",
                joinLines("field str_map type map<string, string> {",
                        "  indexing: summary",
                        "  struct-field key { indexing: attribute }",
                        "  struct-field value { indexing: attribute }",
                        "}"));
        assertTrue(f.isSupportedComplexField());
        assertTrue(f.isMapOfPrimitiveType());
        assertFalse(f.isMapOfSimpleStruct());
        assertTrue(f.isComplexFieldWithOnlyStructFieldAttributes());
    }

    @Test
    public void map_of_primitive_type_with_some_struct_field_attributes_is_tagged_as_such() throws ParseException {
        {
            Fixture f = new Fixture("int_map",
                    joinLines("field int_map type map<int, int> {",
                            "  indexing: summary",
                            "  struct-field key { indexing: attribute }",
                            "}"));
            assertTrue(f.isSupportedComplexField());
            assertTrue(f.isMapOfPrimitiveType());
            assertFalse(f.isMapOfSimpleStruct());
            assertFalse(f.isComplexFieldWithOnlyStructFieldAttributes());
        }
        {
            Fixture f = new Fixture("int_map",
                    joinLines("field int_map type map<int, int> {",
                            "  indexing: summary",
                            "  struct-field value { indexing: attribute }",
                            "}"));
            assertTrue(f.isSupportedComplexField());
            assertTrue(f.isMapOfPrimitiveType());
            assertFalse(f.isMapOfSimpleStruct());
            assertFalse(f.isComplexFieldWithOnlyStructFieldAttributes());
        }
    }

    @Test
    public void unsupported_complex_field_is_tagged_as_such() throws ParseException {
        {
            ComplexFixture f = new ComplexFixture("elem_array",
                    joinLines("field elem_array type array<elem> {",
                            "  struct-field name { indexing: attribute }",
                            "  struct-field weight { indexing: attribute }",
                            "}"));
            assertFalse(f.isSupportedComplexField());
            assertFalse(f.isArrayOfSimpleStruct());
            assertFalse(f.isMapOfSimpleStruct());
            assertFalse(f.isMapOfPrimitiveType());
            assertFalse(f.isComplexFieldWithOnlyStructFieldAttributes());
        }
        {
            ComplexFixture f = new ComplexFixture("elem_map",
                    joinLines("field elem_map type map<int, elem> {",
                            "  indexing: summary",
                            "  struct-field key { indexing: attribute }",
                            "  struct-field value.weight { indexing: attribute }",
                            "}"));
            assertFalse(f.isSupportedComplexField());
            assertFalse(f.isArrayOfSimpleStruct());
            assertFalse(f.isMapOfSimpleStruct());
            assertFalse(f.isMapOfPrimitiveType());
            assertFalse(f.isComplexFieldWithOnlyStructFieldAttributes());
        }
    }

}
