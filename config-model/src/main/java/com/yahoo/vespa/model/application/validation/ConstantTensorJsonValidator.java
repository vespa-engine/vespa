// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.google.common.base.Joiner;
import com.yahoo.tensor.TensorType;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * ConstantTensorJsonValidator strictly validates a constant tensor in JSON format read from a Reader object
 *
 * @author Vegard Sjonfjell
 */
public class ConstantTensorJsonValidator {

    private static final String FIELD_CELLS = "cells";
    private static final String FIELD_ADDRESS = "address";
    private static final String FIELD_VALUE = "value";

    private static final JsonFactory jsonFactory = new JsonFactory();

    private JsonParser parser;
    private Map<String, TensorType.Dimension> tensorDimensions;

    public void validate(String fileName, TensorType type, Reader tensorData) {
        if (fileName.endsWith(".json")) {
            validateTensor(type, tensorData);
        }
        else if (fileName.endsWith(".json.lz4")) {
            // don't validate; the cost probably outweights the advantage
        }
        else if (fileName.endsWith(".tbf")) {
            // don't validate; internal format, so this constant is written by us
        }
        else {
            // (don't mention the internal format to users)
            throw new IllegalArgumentException("Ranking constant file names must end with either '.json' or '.json.lz4'");
        }
    }

    private void validateTensor(TensorType type, Reader tensorData) {
        wrapIOException(() -> {
            this.parser = jsonFactory.createParser(tensorData);
            this.tensorDimensions = type
                    .dimensions()
                    .stream()
                    .collect(Collectors.toMap(TensorType.Dimension::name, Function.identity()));

            assertNextTokenIs(JsonToken.START_OBJECT);
            assertNextTokenIs(JsonToken.FIELD_NAME);
            assertFieldNameIs(FIELD_CELLS);

            assertNextTokenIs(JsonToken.START_ARRAY);

            while (parser.nextToken() != JsonToken.END_ARRAY) {
                validateTensorCell();
            }

            assertNextTokenIs(JsonToken.END_OBJECT);
        });
    }

    private void validateTensorCell() {
        wrapIOException(() -> {
            assertCurrentTokenIs(JsonToken.START_OBJECT);

            List<String> fieldNameCandidates = new ArrayList<>(Arrays.asList(FIELD_ADDRESS, FIELD_VALUE));
            for (int i = 0; i < 2; i++) {
                assertNextTokenIs(JsonToken.FIELD_NAME);
                String fieldName = parser.getCurrentName();

                if (fieldNameCandidates.contains(fieldName)) {
                    fieldNameCandidates.remove(fieldName);

                    if (fieldName.equals(FIELD_ADDRESS)) {
                        validateTensorAddress();
                    } else if (fieldName.equals(FIELD_VALUE)) {
                        validateTensorValue();
                    }
                } else {
                    throw new InvalidConstantTensorException(parser, "Only 'address' or 'value' fields are permitted within a cell object");
                }
            }

            assertNextTokenIs(JsonToken.END_OBJECT);
        });
    }

    private void validateTensorAddress() throws IOException {
        assertNextTokenIs(JsonToken.START_OBJECT);

        Set<String> cellDimensions = new HashSet<>(tensorDimensions.keySet());

        // Iterate within the address key, value pairs
        while ((parser.nextToken() != JsonToken.END_OBJECT)) {
            assertCurrentTokenIs(JsonToken.FIELD_NAME);

            String dimensionName = parser.getCurrentName();
            TensorType.Dimension dimension = tensorDimensions.get(dimensionName);
            if (dimension == null) {
                throw new InvalidConstantTensorException(parser, String.format("Tensor dimension '%s' does not exist", parser.getCurrentName()));
            }

            if (!cellDimensions.contains(dimensionName)) {
                throw new InvalidConstantTensorException(parser, String.format("Duplicate tensor dimension '%s'", parser.getCurrentName()));
            }

            cellDimensions.remove(dimensionName);
            validateLabel(dimension);
        }

        if (!cellDimensions.isEmpty()) {
            throw new InvalidConstantTensorException(parser, String.format("Tensor address missing dimension(s) %s", Joiner.on(", ").join(cellDimensions)));
        }
    }

    /**
     * Tensor labels are always strings. Labels for a mapped dimension can be any string,
     * but those for indexed dimensions needs to be able to be interpreted as integers, and,
     * additionally, those for indexed bounded dimensions needs to fall within the dimension size.
     */
    private void validateLabel(TensorType.Dimension dimension) throws IOException {
        JsonToken token = parser.nextToken();
        if (token != JsonToken.VALUE_STRING)
            throw new InvalidConstantTensorException(parser, String.format("Tensor label is not a string (%s)", token.toString()));

        if (dimension instanceof TensorType.IndexedBoundDimension) {
            validateBoundIndex((TensorType.IndexedBoundDimension) dimension);
        } else if (dimension instanceof TensorType.IndexedUnboundDimension) {
            validateUnboundIndex(dimension);
        }
    }

    private void validateBoundIndex(TensorType.IndexedBoundDimension dimension) {
        wrapIOException(() -> {
            try {
                int value = Integer.parseInt(parser.getValueAsString());
                if (value >= dimension.size().get())
                    throw new InvalidConstantTensorException(parser, String.format("Index %s not within limits of bound dimension '%s'", value, dimension.name()));
            } catch (NumberFormatException e) {
                throwCoordinateIsNotInteger(parser.getValueAsString(), dimension.name());
            }
        });
    }

    private void validateUnboundIndex(TensorType.Dimension dimension) {
        wrapIOException(() -> {
            try {
                Integer.parseInt(parser.getValueAsString());
            } catch (NumberFormatException e) {
                throwCoordinateIsNotInteger(parser.getValueAsString(), dimension.name());
            }
        });
    }

    private void throwCoordinateIsNotInteger(String value, String dimensionName) {
        throw new InvalidConstantTensorException(parser, String.format("Index '%s' for dimension '%s' is not an integer", value, dimensionName));
    }

    private void validateTensorValue() throws IOException {
        JsonToken token = parser.nextToken();

        if (token != JsonToken.VALUE_NUMBER_FLOAT && token != JsonToken.VALUE_NUMBER_INT) {
            throw new InvalidConstantTensorException(parser, String.format("Tensor value is not a number (%s)", token.toString()));
        }
    }

    private void assertCurrentTokenIs(JsonToken wantedToken) {
        assertTokenIs(parser.getCurrentToken(), wantedToken);
    }

    private void assertNextTokenIs(JsonToken wantedToken) throws IOException {
        assertTokenIs(parser.nextToken(), wantedToken);
    }

    private void assertTokenIs(JsonToken token, JsonToken wantedToken) {
        if (token != wantedToken) {
            throw new InvalidConstantTensorException(parser, String.format("Expected JSON token %s, but got %s", wantedToken.toString(), token.toString()));
        }
    }

    private void assertFieldNameIs(String wantedFieldName) throws IOException {
        String actualFieldName = parser.getCurrentName();

        if (!actualFieldName.equals(wantedFieldName)) {
            throw new InvalidConstantTensorException(parser, String.format("Expected field name '%s', got '%s'", wantedFieldName, actualFieldName));
        }
    }

    static class InvalidConstantTensorException extends IllegalArgumentException {

        InvalidConstantTensorException(JsonParser parser, String message) {
            super(message + " " + parser.getCurrentLocation().toString());
        }

        InvalidConstantTensorException(JsonParser parser, Exception base) {
            super("Failed to parse JSON stream " + parser.getCurrentLocation().toString(), base);
        }

    }

    @FunctionalInterface
    private interface SubroutineThrowingIOException {
        void invoke() throws IOException;
    }

    private void wrapIOException(SubroutineThrowingIOException lambda) {
        try {
            lambda.invoke();
        } catch (IOException e) {
            throw new InvalidConstantTensorException(parser, e);
        }
    }

}
