// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.document;

import com.yahoo.schema.Schema;
import com.yahoo.schema.ApplicationBuilder;
import com.yahoo.schema.parser.ParseException;
import org.junit.jupiter.api.Test;

import static com.yahoo.config.model.test.TestUtil.joinLines;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ComplexAttributeFieldUtilsTestCase {

    private static class FixtureBase {

        private final ImmutableSDField field;

        FixtureBase(String fieldName, String sdContent) throws ParseException {
            Schema schema = ApplicationBuilder.createFromString(sdContent).getSchema();
            field = schema.getConcreteField(fieldName);
        }

        public ImmutableSDField field() {
            return field;
        }

        boolean isSupportedComplexField() {
            return ComplexAttributeFieldUtils.isSupportedComplexField(field());
        }

        boolean isArrayOfSimpleStruct() {
            return ComplexAttributeFieldUtils.isArrayOfSimpleStruct(field());
        }

        boolean isMapOfSimpleStruct() {
            return ComplexAttributeFieldUtils.isMapOfSimpleStruct(field());
        }

        boolean isMapOfPrimitiveType() {
            return ComplexAttributeFieldUtils.isMapOfPrimitiveType(field());
        }

        boolean isComplexFieldWithOnlyStructFieldAttributes() {
            return ComplexAttributeFieldUtils.isComplexFieldWithOnlyStructFieldAttributes(field());
        }
    }

    private static class Fixture extends FixtureBase {

        Fixture(String fieldName, String sdFieldContent) throws ParseException {
            super(fieldName, joinLines("search test {",
                    "  document test {",
                    "    struct elem {",
                    "      field name type string {}",
                    "      field weight type int {}",
                    "    }",
                    sdFieldContent,
                    "  }",
                    "}"));
        }
    }

    private static class ComplexFixture extends FixtureBase {

        ComplexFixture(String fieldName, String sdFieldContent) throws ParseException {
            super(fieldName, joinLines("search test {",
                    "  document test {",
                    "    struct elem {",
                    "      field name type string {}",
                    "      field weights type array<int> {}",
                    "    }",
                    sdFieldContent,
                    "  }",
                    "}"));
        }
    }

    @Test
    void array_of_struct_with_only_struct_field_attributes_is_tagged_as_such() throws ParseException {
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
    void array_of_struct_with_some_struct_field_attributes_is_tagged_as_such() throws ParseException {
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
    void map_of_struct_with_only_struct_field_attributes_is_tagged_as_such() throws ParseException {
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
    void map_of_struct_with_some_struct_field_attributes_is_tagged_as_such() throws ParseException {
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
    void map_of_primitive_type_with_only_struct_field_attributes_is_tagged_as_such() throws ParseException {
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
    void map_of_primitive_type_with_some_struct_field_attributes_is_tagged_as_such() throws ParseException {
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
    void unsupported_complex_field_is_tagged_as_such() throws ParseException {
        {
            ComplexFixture f = new ComplexFixture("elem_array",
                    joinLines("field elem_array type array<elem> {",
                            "  struct-field name { indexing: attribute }",
                            "  struct-field weights { indexing: attribute }",
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
                            "  struct-field value.weights { indexing: attribute }",
                            "}"));
            assertFalse(f.isSupportedComplexField());
            assertFalse(f.isArrayOfSimpleStruct());
            assertFalse(f.isMapOfSimpleStruct());
            assertFalse(f.isMapOfPrimitiveType());
            assertFalse(f.isComplexFieldWithOnlyStructFieldAttributes());
        }
    }

    @Test
    void only_struct_field_attributes_are_considered_when_tagging_a_complex_field() throws ParseException {
        {
            ComplexFixture f = new ComplexFixture("elem_array",
                    joinLines("field elem_array type array<elem> {",
                            "  struct-field name { indexing: attribute }",
                            "}"));
            assertTrue(f.isSupportedComplexField());
            assertTrue(f.isArrayOfSimpleStruct());
            assertFalse(f.isMapOfSimpleStruct());
            assertFalse(f.isMapOfPrimitiveType());
            assertFalse(f.isComplexFieldWithOnlyStructFieldAttributes());
        }
        {
            ComplexFixture f = new ComplexFixture("elem_map",
                    joinLines("field elem_map type map<int, elem> {",
                            "  indexing: summary",
                            "  struct-field key { indexing: attribute }",
                            "  struct-field value.name { indexing: attribute }",
                            "}"));
            assertTrue(f.isSupportedComplexField());
            assertFalse(f.isArrayOfSimpleStruct());
            assertTrue(f.isMapOfSimpleStruct());
            assertFalse(f.isMapOfPrimitiveType());
            assertFalse(f.isComplexFieldWithOnlyStructFieldAttributes());
        }
    }

}
