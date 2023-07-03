// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation;

import com.yahoo.tensor.TensorType;
import org.junit.jupiter.api.Test;

import java.io.Reader;
import java.io.StringReader;

import static com.yahoo.test.json.JsonTestHelper.inputJson;
import static com.yahoo.vespa.model.application.validation.ConstantTensorJsonValidator.InvalidConstantTensorException;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ConstantTensorJsonValidatorTest {

    private static Reader inputJsonToReader(String... lines) {
        return new StringReader(inputJson(lines));
    }

    private static void validateTensorJson(TensorType tensorType, Reader jsonTensorReader) {
        new ConstantTensorJsonValidator(tensorType).validate("dummy.json", jsonTensorReader);
    }

    @Test
    void ensure_that_unbounded_tensor_works() {
        validateTensorJson(
                TensorType.fromSpec("tensor(x[], y[])"),
                inputJsonToReader(
                        "{",
                        "   'cells': [",
                        "        {",
                        "            'address': { 'x': '99999', 'y': '47' },",
                        "            'value': 9932.0",
                        "        }",
                        "   ]",
                        "}"));
    }

    @Test
    void ensure_that_bounded_tensor_within_limits_works() {
        validateTensorJson(
                TensorType.fromSpec("tensor(x[5], y[10])"),
                inputJsonToReader(
                        "{",
                        "   'cells': [",
                        "        {",
                        "            'address': { 'x': '3', 'y': '2' },",
                        "            'value': 2.0",
                        "        }",
                        "   ]",
                        "}"));
    }

    @Test
    void ensure_that_multiple_cells_work() {
        validateTensorJson(
                TensorType.fromSpec("tensor(x[], y[])"),
                inputJsonToReader(
                        "{",
                        "   'cells': [",
                        "        {",
                        "            'address': { 'x': '3', 'y': '2' },",
                        "            'value': 2.0",
                        "        },",
                        "        {",
                        "            'address': { 'x': '2', 'y': '0' },",
                        "            'value': 45",
                        "        }",
                        "   ]",
                        "}"));
    }


    @Test
    void ensure_that_no_cells_work() {
        validateTensorJson(
                TensorType.fromSpec("tensor(x[], y[])"),
                inputJsonToReader(
                        "{",
                        "   'cells': []",
                        "}"));
    }

    @Test
    void ensure_that_bound_tensor_outside_limits_is_disallowed() {
        Throwable exception = assertThrows(InvalidConstantTensorException.class, () -> {

            validateTensorJson(
                    TensorType.fromSpec("tensor(x[5], y[10])"),
                    inputJsonToReader(
                            "{",
                            "   'cells': [",
                            "        {",
                            "            'address': { 'x': '5', 'y': '2' },",
                            "            'value': 1e47",
                            "        }",
                            "   ]",
                            "}"));
        });
        assertTrue(exception.getMessage().contains("Index 5 not within limits of bound dimension 'x'"));
    }

    @Test
    void ensure_that_mapped_tensor_works() {
        validateTensorJson(
                TensorType.fromSpec("tensor(x{}, y{})"),
                inputJsonToReader(
                        "{",
                        "   'cells': [",
                        "        {",
                        "            'address': { 'x': 'andrei', 'y': 'bjarne' },",
                        "            'value': 2.0",
                        "        }",
                        "   ]",
                        "}"));
    }

    @Test
    void ensure_that_non_integer_strings_in_address_points_are_disallowed_unbound() {
        Throwable exception = assertThrows(InvalidConstantTensorException.class, () -> {

            validateTensorJson(
                    TensorType.fromSpec("tensor(x[])"),
                    inputJsonToReader(
                            "{",
                            "   'cells': [",
                            "        {",
                            "            'address': { 'x': 'a' },",
                            "            'value': 47.0",
                            "        }",
                            "   ]",
                            "}"));
        });
        assertTrue(exception.getMessage().contains("Index 'a' for dimension 'x' is not an integer"));
    }

    @Test
    void ensure_that_tensor_coordinates_are_strings() {
        Throwable exception = assertThrows(InvalidConstantTensorException.class, () -> {

            validateTensorJson(
                    TensorType.fromSpec("tensor(x[])"),
                    inputJsonToReader(
                            "{",
                            "   'cells': [",
                            "        {",
                            "            'address': { 'x': 47 },",
                            "            'value': 33.0",
                            "        }",
                            "   ]",
                            "}"));
        });
        assertTrue(exception.getMessage().contains("Tensor label is not a string (VALUE_NUMBER_INT)"));
    }

    @Test
    void ensure_that_non_integer_strings_in_address_points_are_disallowed_bounded() {
        Throwable exception = assertThrows(InvalidConstantTensorException.class, () -> {

            validateTensorJson(
                    TensorType.fromSpec("tensor(x[5])"),
                    inputJsonToReader(
                            "{",
                            "   'cells': [",
                            "        {",
                            "            'address': { 'x': 'a' },",
                            "            'value': 41.0",
                            "        }",
                            "   ]",
                            "}"));
        });
        assertTrue(exception.getMessage().contains("Index 'a' for dimension 'x' is not an integer"));
    }

    @Test
    void ensure_that_missing_coordinates_fail() {
        Throwable exception = assertThrows(InvalidConstantTensorException.class, () -> {

            validateTensorJson(
                    TensorType.fromSpec("tensor(x[], y[], z[])"),
                    inputJsonToReader(
                            "{",
                            "   'cells': [",
                            "        {",
                            "            'address': { 'x': '3' },",
                            "            'value': 99.3",
                            "        }",
                            "   ]",
                            "}"));
        });
        assertTrue(exception.getMessage().contains("Tensor address missing dimension(s) y, z"));
    }

    @Test
    void ensure_that_non_number_values_are_disallowed() {
        Throwable exception = assertThrows(InvalidConstantTensorException.class, () -> {

            validateTensorJson(
                    TensorType.fromSpec("tensor(x[])"),
                    inputJsonToReader(
                            "{",
                            "   'cells': [",
                            "        {",
                            "            'address': { 'x': '3' },",
                            "            'value': 'fruit'",
                            "        }",
                            "   ]",
                            "}"));
            });
        assertTrue(exception.getMessage().contains("Inside 'value': cell value is not a number (VALUE_STRING)"));
    }

    @Test
    void ensure_that_extra_dimensions_are_disallowed() {
        Throwable exception = assertThrows(InvalidConstantTensorException.class, () -> {

            validateTensorJson(
                    TensorType.fromSpec("tensor(x[], y[])"),
                    inputJsonToReader(
                            "{",
                            "   'cells': [",
                            "        {",
                            "            'address': { 'x': '3', 'y': '2', 'z': '4' },",
                            "            'value': 99.3",
                            "        }",
                            "   ]",
                            "}"));
        });
        assertTrue(exception.getMessage().contains("Tensor dimension 'z' does not exist"));
    }

    @Test
    void ensure_that_duplicate_dimensions_are_disallowed() {
        Throwable exception = assertThrows(InvalidConstantTensorException.class, () -> {

            validateTensorJson(
                    TensorType.fromSpec("tensor(x[], y[])"),
                    inputJsonToReader(
                            "{",
                            "   'cells': [",
                            "        {",
                            "            'address': { 'x': '1', 'y': '2', 'y': '4' },",
                            "            'value': 88.1",
                            "        }",
                            "   ]",
                            "}"));
        });
        assertTrue(exception.getMessage().contains("Duplicate tensor dimension 'y'"));
    }

    @Test
    void ensure_that_invalid_json_fails() {
        Throwable exception = assertThrows(InvalidConstantTensorException.class, () -> {

            validateTensorJson(
                    TensorType.fromSpec("tensor(x[], y[])"),
                    inputJsonToReader(
                            "{",
                            "    cells': [",
                            "        {",
                            "            'address': { 'x': '3' 'y': '2' }",
                            "            'value': 2.0",
                            "        }",
                            "   ",
                            "}"));
        });
        assertTrue(exception.getMessage().contains("Failed to parse JSON stream"));
    }

    @Test
    void ensure_that_invalid_json_not_in_tensor_format_fails() {
        Throwable exception = assertThrows(InvalidConstantTensorException.class, () -> {

            validateTensorJson(TensorType.fromSpec("tensor(x[], y[])"),
                    inputJsonToReader(
                            "{",
                            "   'stats': {",
                            "       '\u30d1\u30fc\u30d7\u30eb\u30b4\u30e0\u88fd\u306e\u30a2\u30d2\u30eb\u306f\u79c1\u3092\u6bba\u3059\u305f\u3081\u306b\u671b\u3093\u3067\u3044\u307e\u3059': true,",
                            "       'points': 47",
                            "   }",
                            "}"));
        });
        assertTrue(exception.getMessage().contains("Unexpected content '{' for field 'stats'"));
    }

    @Test
    void ensure_that_values_array_for_vector_works() {
        validateTensorJson(
                TensorType.fromSpec("tensor(x[5])"),
                inputJsonToReader("[5,4.0,3.1,-2,-1.0]"));
        validateTensorJson(
                TensorType.fromSpec("tensor(x[5])"),
                inputJsonToReader("{'values':[5,4.0,3.1,-2,-1.0]}"));
    }

    @Test
    void ensure_that_simple_object_for_map_works() {
        validateTensorJson(
                TensorType.fromSpec("tensor(x{})"),
                inputJsonToReader("{'cells':{'a':5,'b':4.0,'c':3.1,'d':-2,'e':-1.0}}"));
    }

    @Test
    void ensure_that_matrices_work() {
        validateTensorJson(
                TensorType.fromSpec("tensor(x[2], y[3])"),
                inputJsonToReader(
                        "[",
                        " [ 1, 2, 3],",
                        " [ 4, 5, 6]",
                        "]"));
        validateTensorJson(
                TensorType.fromSpec("tensor(x[2], y[3])"),
                inputJsonToReader(
                        "{'values':[",
                        " [ 1, 2, 3],",
                        " [ 4, 5, 6]",
                        "]}"));
    }

    @Test
    void ensure_that_simple_maps_work() {
        validateTensorJson(
                TensorType.fromSpec("tensor(category{})"),
                inputJsonToReader(
                        "{",
                        "   'foo': 1,",
                        "   'bar': 2,",
                        "   'type': 3,",
                        "   'cells': 4,",
                        "   'value': 5,",
                        "   'values': 6,",
                        "   'blocks': 7,",
                        "   'anything': 8",
                        "}"));
        validateTensorJson(
                TensorType.fromSpec("tensor(category{})"),
                inputJsonToReader(
                        "{'cells':{",
                        "   'foo': 1,",
                        "   'bar': 2,",
                        "   'type': 3,",
                        "   'cells': 4,",
                        "   'value': 5,",
                        "   'values': 6,",
                        "   'blocks': 7,",
                        "   'anything': 8",
                        "}}"));
    }

    @Test
    void ensure_that_mixing_formats_disallowed() {
        Throwable exception = assertThrows(InvalidConstantTensorException.class, () -> {
            validateTensorJson(
                    TensorType.fromSpec("tensor(x{})"),
                    inputJsonToReader("{ 'a': 1.0, 'cells': { 'b': 2.0 } }"));

        });
        assertTrue(exception.getMessage().contains("Cannot use {label: value} format together with 'cells'"));
    }

    @Test
    void ensure_that_simple_blocks_work() {
        validateTensorJson(
                TensorType.fromSpec("tensor(a{},b[3])"),
                inputJsonToReader(
                        "{'blocks':{'foo':[1,2,3], 'bar':[4,5,6]}}"));
    }

    @Test
    void ensure_that_complex_blocks_work() {
        validateTensorJson(
                TensorType.fromSpec("tensor(a{},b[3],c{},d[2])"),
                inputJsonToReader(
                        "{'blocks':[",
                        "{'address':{'a':'foo','c':'bar'},'values':[[1,2],[3,4],[5,6]]},",
                        "{'address':{'a':'qux','c':'zip'},'values':[[9,8],[7,6],[5,4]]}]}"));
    }

}
