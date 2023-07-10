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
import java.util.HashMap;
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
 * @author arnej
 */
public class ConstantTensorJsonValidator {

    private static final String FIELD_CELLS = "cells";
    private static final String FIELD_ADDRESS = "address";
    private static final String FIELD_VALUE = "value";
    private static final String FIELD_VALUES = "values";
    private static final String FIELD_BLOCKS = "blocks";
    private static final String FIELD_TYPE = "type";

    private static final JsonFactory jsonFactory = new JsonFactory();

    private JsonParser parser;
    private final TensorType tensorType;
    private final Map<String, TensorType.Dimension> tensorDimensions = new HashMap<>();
    private final List<String> denseDims = new ArrayList<>();
    private final List<String> mappedDims = new ArrayList<>();
    private int numIndexedDims = 0;
    private int numMappedDims = 0;
    private boolean seenCells = false;
    private boolean seenValues = false;
    private boolean seenBlocks = false;
    private boolean seenType = false;
    private boolean seenSimpleMapValue = false;

    private boolean isScalar() {
        return (numIndexedDims == 0 && numMappedDims == 0);
    }
    private boolean isDense() {
        return (numIndexedDims > 0 && numMappedDims == 0);
    }
    private boolean isSparse() {
        return (numIndexedDims == 0 && numMappedDims > 0);
    }
    private boolean isSingleDense() {
        return (numIndexedDims == 1 && numMappedDims == 0);
    }
    private boolean isSingleSparse() {
        return (numIndexedDims == 0 && numMappedDims == 1);
    }
    private boolean isMixed() {
        return (numIndexedDims > 0 && numMappedDims > 0);
    }

    public ConstantTensorJsonValidator(TensorType type) {
        this.tensorType = type;
        for (var dim : type.dimensions()) {
            tensorDimensions.put(dim.name(), dim);
            switch (dim.type()) {
            case mapped:
                ++numMappedDims;
                mappedDims.add(dim.name());
                break;
            case indexedBound:
            case indexedUnbound:
                ++numIndexedDims;
                denseDims.add(dim.name());
            }
        }
    }

    public void validate(String fileName, Reader tensorData) {
        if (fileName.endsWith(".json")) {
            validateTensor(tensorData);
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

    private void validateTensor(Reader tensorData) {
        try {
            this.parser = jsonFactory.createParser(tensorData);
            var top = parser.nextToken();
            if (top == JsonToken.START_ARRAY && isDense()) {
                consumeValuesArray();
                return;
            } else if (top == JsonToken.START_OBJECT) {
                consumeTopObject();
                return;
            } else if (isScalar()) {
                throw new InvalidConstantTensorException(
                        parser, String.format("Invalid type %s: Only tensors with dimensions can be stored as file constants", tensorType.toString()));
            }
            throw new InvalidConstantTensorException(
                    parser, String.format("Unexpected first token '%s' for constant with type %s",
                                          parser.getText(), tensorType.toString()));
        } catch (IOException e) {
            if (parser != null) {
                throw new InvalidConstantTensorException(parser, e);
            }
            throw new InvalidConstantTensorException(e);
        }
    }

    private void consumeValuesArray() throws IOException {
        consumeValuesNesting(0);
    }

    private void consumeTopObject() throws IOException {
        for (var cur = parser.nextToken(); cur != JsonToken.END_OBJECT; cur = parser.nextToken()) {
            assertCurrentTokenIs(JsonToken.FIELD_NAME);
            String fieldName = parser.getCurrentName();
            switch (fieldName) {
            case FIELD_TYPE:
                consumeTypeField();
                break;
            case FIELD_VALUES:
                consumeValuesField();
                break;
            case FIELD_CELLS:
                consumeCellsField();
                break;
            case FIELD_BLOCKS:
                consumeBlocksField();
                break;
            default:
                consumeAnyField(fieldName, parser.nextToken());
                break;
            }
        }
        if (seenSimpleMapValue) {
            if (! isSingleSparse()) {
                throw new InvalidConstantTensorException(parser, String.format("Cannot use {label: value} format for constant of type %s", tensorType.toString()));
            }
            if (seenCells || seenValues || seenBlocks || seenType) {
                throw new InvalidConstantTensorException(parser, String.format("Cannot use {label: value} format together with '%s'",
                                                                               (seenCells ? FIELD_CELLS :
                                                                                (seenValues ? FIELD_VALUES :
                                                                                 (seenBlocks ? FIELD_BLOCKS : FIELD_TYPE)))));
            }
        }
        if (seenCells) {
            if (seenValues || seenBlocks) {
                throw new InvalidConstantTensorException(parser, String.format("Cannot use both '%s' and '%s' at the same time",
                                                                               FIELD_CELLS, (seenValues ? FIELD_VALUES : FIELD_BLOCKS)));
            }
        }
        if (seenValues && seenBlocks) {
            throw new InvalidConstantTensorException(parser, String.format("Cannot use both '%s' and '%s' at the same time",
                                                                           FIELD_VALUES, FIELD_BLOCKS));
        }
    }

    private void consumeCellsField() throws IOException {
        var cur = parser.nextToken();
        if (cur == JsonToken.START_ARRAY) {
            consumeLiteralFormArray();
            seenCells = true;
        } else if (cur == JsonToken.START_OBJECT) {
            consumeSimpleMappedObject();
            seenCells = true;
        } else {
            consumeAnyField(FIELD_BLOCKS, cur);
        }
    }

    private void consumeLiteralFormArray() throws IOException {
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            validateLiteralFormCell();
        }
    }

    private void consumeSimpleMappedObject() throws IOException {
        if (! isSingleSparse()) {
            throw new InvalidConstantTensorException(parser, String.format("Cannot use {label: value} format for constant of type %s", tensorType.toString()));
        }
        for (var cur = parser.nextToken(); cur != JsonToken.END_OBJECT; cur = parser.nextToken()) {
            assertCurrentTokenIs(JsonToken.FIELD_NAME);
            validateNumeric(parser.getCurrentName(), parser.nextToken());
        }
    }

    private void validateLiteralFormCell() throws IOException {
        assertCurrentTokenIs(JsonToken.START_OBJECT);
        boolean seenAddress = false;
        boolean seenValue = false;
        for (int i = 0; i < 2; i++) {
            assertNextTokenIs(JsonToken.FIELD_NAME);
            String fieldName = parser.getCurrentName();
            switch (fieldName) {
            case FIELD_ADDRESS:
                validateTensorAddress(new HashSet<>(tensorDimensions.keySet()));
                seenAddress = true;
                break;
            case FIELD_VALUE:
                validateNumeric(FIELD_VALUE, parser.nextToken());
                seenValue = true;
                break;
            default:
                throw new InvalidConstantTensorException(parser, String.format("Only '%s' or '%s' fields are permitted within a cell object",
                                                                               FIELD_ADDRESS, FIELD_VALUE));
            }
        }
        if (! seenAddress) {
            throw new InvalidConstantTensorException(parser, String.format("Missing '%s' field in cell object", FIELD_ADDRESS));
        }
        if (! seenValue) {
            throw new InvalidConstantTensorException(parser, String.format("Missing '%s' field in cell object", FIELD_VALUE));
        }
        assertNextTokenIs(JsonToken.END_OBJECT);
    }

    private void validateTensorAddress(Set<String> cellDimensions) throws IOException {
        assertNextTokenIs(JsonToken.START_OBJECT);
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
        if (token != JsonToken.VALUE_STRING) {
            throw new InvalidConstantTensorException(parser, String.format("Tensor label is not a string (%s)", token.toString()));
        }
        if (dimension instanceof TensorType.IndexedBoundDimension) {
            validateBoundIndex((TensorType.IndexedBoundDimension) dimension);
        } else if (dimension instanceof TensorType.IndexedUnboundDimension) {
            validateUnboundIndex(dimension);
        }
    }

    private void validateBoundIndex(TensorType.IndexedBoundDimension dimension) throws IOException {
        try {
            int value = Integer.parseInt(parser.getValueAsString());
            if (value >= dimension.size().get())
                throw new InvalidConstantTensorException(parser, String.format("Index %s not within limits of bound dimension '%s'", value, dimension.name()));
        } catch (NumberFormatException e) {
            throwCoordinateIsNotInteger(parser.getValueAsString(), dimension.name());
        }
    }

    private void validateUnboundIndex(TensorType.Dimension dimension) throws IOException {
        try {
            Integer.parseInt(parser.getValueAsString());
        } catch (NumberFormatException e) {
            throwCoordinateIsNotInteger(parser.getValueAsString(), dimension.name());
        }
    }

    private void throwCoordinateIsNotInteger(String value, String dimensionName) {
        throw new InvalidConstantTensorException(parser, String.format("Index '%s' for dimension '%s' is not an integer", value, dimensionName));
    }

    private void validateNumeric(String where, JsonToken token) throws IOException {
        if (token != JsonToken.VALUE_NUMBER_FLOAT && token != JsonToken.VALUE_NUMBER_INT) {
            throw new InvalidConstantTensorException(parser, String.format("Inside '%s': cell value is not a number (%s)", where, token.toString()));
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

    static class InvalidConstantTensorException extends IllegalArgumentException {

        InvalidConstantTensorException(JsonParser parser, String message) {
            super(message + " " + parser.getCurrentLocation().toString());
        }

        InvalidConstantTensorException(JsonParser parser, Exception base) {
            super("Failed to parse JSON stream " + parser.getCurrentLocation().toString(), base);
        }

        InvalidConstantTensorException(IOException base) {
            super("Failed to parse JSON stream: " + base.getMessage(), base);
        }
    }

    private void consumeValuesNesting(int level) throws IOException {
        assertCurrentTokenIs(JsonToken.START_ARRAY);
        if (level >= denseDims.size()) {
            throw new InvalidConstantTensorException(
                    parser, String.format("Too deep array nesting for constant with type %s", tensorType.toString()));
        }
        var dim = tensorDimensions.get(denseDims.get(level));
        long count = 0;
        for (var cur = parser.nextToken(); cur != JsonToken.END_ARRAY; cur = parser.nextToken()) {
            if (level + 1 == denseDims.size()) {
                validateNumeric(FIELD_VALUES, cur);
            } else if (cur == JsonToken.START_ARRAY) {
                consumeValuesNesting(level + 1);
            } else {
                throw new InvalidConstantTensorException(
                        parser, String.format("Unexpected token %s '%s'", cur.toString(), parser.getText()));
            }
            ++count;
        }
        if (dim.size().isPresent()) {
            var sz = dim.size().get();
            if (sz != count) {
                throw new InvalidConstantTensorException(
                        parser, String.format("Dimension '%s' has size %d but array had %d values", dim.name(), sz, count));
            }
        }
    }

    private void consumeTypeField() throws IOException {
        var cur = parser.nextToken();
        if (cur == JsonToken.VALUE_STRING) {
            seenType = true;
        } else if (isSingleSparse()) {
            validateNumeric(FIELD_TYPE, cur);
            seenSimpleMapValue = true;
        } else {
            throw new InvalidConstantTensorException(
                    parser, String.format("Field '%s' should contain the tensor type as a string, got %s", FIELD_TYPE, parser.getText()));
        }
    }

    private void consumeValuesField() throws IOException {
        var cur = parser.nextToken();
        if (isDense() && cur == JsonToken.START_ARRAY) {
            consumeValuesArray();
            seenValues = true;
        } else {
            consumeAnyField(FIELD_VALUES, cur);
        }
    }

    private void consumeBlocksField() throws IOException {
        var cur = parser.nextToken();
        if (cur == JsonToken.START_ARRAY) {
            consumeBlocksArray();
            seenBlocks = true;
        } else if (cur == JsonToken.START_OBJECT) {
            consumeBlocksObject();
            seenBlocks = true;
        } else {
            consumeAnyField(FIELD_BLOCKS, cur);
        }
    }

    private void consumeAnyField(String fieldName, JsonToken cur) throws IOException {
        if (isSingleSparse()) {
            validateNumeric(FIELD_CELLS, cur);
            seenSimpleMapValue = true;
        } else {
            throw new InvalidConstantTensorException(
                    parser, String.format("Unexpected content '%s' for field '%s'", parser.getText(), fieldName));
        }
    }

    private void consumeBlocksArray() throws IOException {
        if (! isMixed()) {
            throw new InvalidConstantTensorException(parser, String.format("Cannot use blocks format:[] for constant of type %s", tensorType.toString()));
        }
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            assertCurrentTokenIs(JsonToken.START_OBJECT);
            boolean seenAddress = false;
            boolean seenValues = false;
            for (int i = 0; i < 2; i++) {
                assertNextTokenIs(JsonToken.FIELD_NAME);
                String fieldName = parser.getCurrentName();
                switch (fieldName) {
                case FIELD_ADDRESS:
                    validateTensorAddress(new HashSet<>(mappedDims));
                    seenAddress = true;
                    break;
                case FIELD_VALUES:
                    assertNextTokenIs(JsonToken.START_ARRAY);
                    consumeValuesArray();
                    seenValues = true;
                    break;
                default:
                    throw new InvalidConstantTensorException(parser, String.format("Only '%s' or '%s' fields are permitted within a block object",
                                                                                   FIELD_ADDRESS, FIELD_VALUES));
                }
            }
            if (! seenAddress) {
                throw new InvalidConstantTensorException(parser, String.format("Missing '%s' field in block object", FIELD_ADDRESS));
            }
            if (! seenValues) {
                throw new InvalidConstantTensorException(parser, String.format("Missing '%s' field in block object", FIELD_VALUES));
            }
            assertNextTokenIs(JsonToken.END_OBJECT);
        }
    }

    private void consumeBlocksObject() throws IOException {
        if (numMappedDims > 1 || ! isMixed()) {
            throw new InvalidConstantTensorException(parser, String.format("Cannot use blocks:{} format for constant of type %s", tensorType.toString()));
        }
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            assertCurrentTokenIs(JsonToken.FIELD_NAME);
            assertNextTokenIs(JsonToken.START_ARRAY);
            consumeValuesArray();
        }
    }

}
