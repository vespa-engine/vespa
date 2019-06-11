// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor;

import java.util.Optional;

/**
 * @author bratseth
 */
class TensorParser {

    static Tensor tensorFrom(String tensorString, Optional<TensorType> explicitType) {
        Optional<TensorType> type;
        String valueString;

        tensorString = tensorString.trim();
        if (tensorString.startsWith("tensor")) {
            int colonIndex = tensorString.indexOf(':');
            String typeString = tensorString.substring(0, colonIndex);
            TensorType typeFromString = TensorTypeParser.fromSpec(typeString);
            if (explicitType.isPresent() && ! explicitType.get().equals(typeFromString))
                throw new IllegalArgumentException("Got tensor with type string '" + typeString + "', but was " +
                                                   "passed type " + explicitType.get());
            type = Optional.of(typeFromString);
            valueString = tensorString.substring(colonIndex + 1);
        }
        else {
            type = explicitType;
            valueString = tensorString;
        }

        valueString = valueString.trim();
        if (valueString.startsWith("{")) {
            return tensorFromSparseValueString(valueString, type);
        }
        else if (valueString.startsWith("[")) {
            return tensorFromDenseValueString(valueString, type);
        }
        else {
            if (explicitType.isPresent() && ! explicitType.get().equals(TensorType.empty))
                throw new IllegalArgumentException("Got a zero-dimensional tensor value ('" + tensorString +
                                                   "') where type " + explicitType.get() + " is required");
            try {
                return Tensor.Builder.of(TensorType.empty).cell(Double.parseDouble(tensorString)).build();
            }
            catch (NumberFormatException e) {
                throw new IllegalArgumentException("Excepted a number or a string starting by {, [ or tensor(...):, got '" +
                                                   tensorString + "'");
            }
        }
    }

    /** Derives the tensor type from the first address string in the given tensor string */
    private static TensorType typeFromSparseValueString(String valueString) {
        String s = valueString.substring(1).trim(); // remove tensor start
        int firstKeyOrTensorEnd = s.indexOf('}');
        if (firstKeyOrTensorEnd < 0)
            throw new IllegalArgumentException("Excepted a number or a string starting by {, [ or tensor(...):, got '" +
                                               valueString + "'");
        String addressBody = s.substring(0, firstKeyOrTensorEnd).trim();
        if (addressBody.isEmpty()) return TensorType.empty; // Empty tensor
        if ( ! addressBody.startsWith("{")) return TensorType.empty; // Single value tensor

        addressBody = addressBody.substring(1, addressBody.length()); // remove key start
        if (addressBody.isEmpty()) return TensorType.empty; // Empty key

        TensorType.Builder builder = new TensorType.Builder(TensorType.Value.DOUBLE);
        for (String elementString : addressBody.split(",")) {
            String[] pair = elementString.split(":");
            if (pair.length != 2)
                throw new IllegalArgumentException("Expecting argument elements to be on the form dimension:label, " +
                                                   "got '" + elementString + "'");
            builder.mapped(pair[0].trim());
        }

        return builder.build();
    }

    private static Tensor tensorFromSparseValueString(String valueString, Optional<TensorType> type) {
        try {
            valueString = valueString.trim();
            Tensor.Builder builder = Tensor.Builder.of(type.orElse(typeFromSparseValueString(valueString)));
            return fromCellString(builder, valueString);
        }
        catch (NumberFormatException e) {
            throw new IllegalArgumentException("Excepted a number or a string starting by { or tensor(, got '" +
                                               valueString + "'");
        }
    }

    private static Tensor tensorFromDenseValueString(String valueString, Optional<TensorType> type) {
        if (type.isEmpty())
            throw new IllegalArgumentException("The dense tensor form requires an explicit tensor type " +
                                               "on the form 'tensor(dimensions):...");
        if (type.get().dimensions().stream().anyMatch(d -> ( d.size().isEmpty())))
            throw new IllegalArgumentException("The dense tensor form requires a tensor type containing " +
                                               "only dense dimensions with a given size");

        IndexedTensor.BoundBuilder builder = (IndexedTensor.BoundBuilder)IndexedTensor.Builder.of(type.get());
        long index = 0;
        int currentChar;
        int nextNumberEnd = 0;
        // Since we know the dimensions the brackets are just syntactic sugar:
        while ((currentChar = nextStartCharIndex(nextNumberEnd + 1, valueString)) < valueString.length()) {
            nextNumberEnd   = nextStopCharIndex(currentChar, valueString);
            if (currentChar == nextNumberEnd) return builder.build();

            TensorType.Value cellValueType = builder.type().valueType();
            String cellValueString = valueString.substring(currentChar, nextNumberEnd);
            try {
                if (cellValueType == TensorType.Value.DOUBLE)
                    builder.cellByDirectIndex(index, Double.parseDouble(cellValueString));
                else if (cellValueType == TensorType.Value.FLOAT)
                    builder.cellByDirectIndex(index, Float.parseFloat(cellValueString));
                else
                    throw new IllegalArgumentException(cellValueType + " is not supported");
            }
            catch (NumberFormatException e) {
                throw new IllegalArgumentException("At index " + index + ": '" +
                                                   cellValueString + "' is not a valid " + cellValueType);
            }
            index++;
        }
        return builder.build();
    }

    /** Returns the position of the next character that should contain a number, or if none the string length */
    private static int nextStartCharIndex(int charIndex, String valueString) {
        for (; charIndex < valueString.length(); charIndex++) {
            if (valueString.charAt(charIndex) == ']') continue;
            if (valueString.charAt(charIndex) == '[') continue;
            if (valueString.charAt(charIndex) == ',') continue;
            if (valueString.charAt(charIndex) == ' ') continue;
            return charIndex;
        }
        return valueString.length();
    }

    private static int nextStopCharIndex(int charIndex, String valueString) {
        while (charIndex < valueString.length()) {
            if (valueString.charAt(charIndex) == ',') return charIndex;
            if (valueString.charAt(charIndex) == ']') return charIndex;
            charIndex++;
        }
        throw new IllegalArgumentException("Malformed tensor value '" + valueString +
                                           "': Expected a ',' or ']' after position " + charIndex);
    }

    private static Tensor fromCellString(Tensor.Builder builder, String s) {
        int index = 1;
        index = skipSpace(index, s);
        while (index + 1 < s.length()) {
            int keyOrTensorEnd = s.indexOf('}', index);
            TensorAddress.Builder addressBuilder = new TensorAddress.Builder(builder.type());
            if (keyOrTensorEnd < s.length() - 1) { // Key end: This has a key - otherwise TensorAddress is empty
                addLabels(s.substring(index, keyOrTensorEnd + 1), addressBuilder);
                index = keyOrTensorEnd + 1;
                index = skipSpace(index, s);
                if ( s.charAt(index) != ':')
                    throw new IllegalArgumentException("Expecting a ':' after " + s.substring(index) + ", got '" + s + "'");
                index++;
            }
            int valueEnd = s.indexOf(',', index);
            if (valueEnd < 0) { // last value
                valueEnd = s.indexOf('}', index);
                if (valueEnd < 0)
                    throw new IllegalArgumentException("A tensor string must end by '}'");
            }

            TensorAddress address = addressBuilder.build();
            TensorType.Value cellValueType = builder.type().valueType();
            String cellValueString = s.substring(index, valueEnd).trim();
            try {
                if (cellValueType == TensorType.Value.DOUBLE)
                    builder.cell(address, Double.parseDouble(cellValueString));
                else if (cellValueType == TensorType.Value.FLOAT)
                    builder.cell(address, Float.parseFloat(cellValueString));
                else
                    throw new IllegalArgumentException(cellValueType + " is not supported");
            }
            catch (NumberFormatException e) {
                throw new IllegalArgumentException("At " + address.toString(builder.type()) + ": '" +
                                                   cellValueString + "' is not a valid " + cellValueType);
            }

            index = valueEnd+1;
            index = skipSpace(index, s);
        }
        return builder.build();
    }

    private static int skipSpace(int index, String s) {
        while (index < s.length() && s.charAt(index) == ' ')
            index++;
        return index;
    }

    /** Creates a tenor address from a string on the form {dimension1:label1,dimension2:label2,...} */
    private static void addLabels(String mapAddressString, TensorAddress.Builder builder) {
        mapAddressString = mapAddressString.trim();
        if ( ! (mapAddressString.startsWith("{") && mapAddressString.endsWith("}")))
            throw new IllegalArgumentException("Expecting a tensor address enclosed in {}, got '" + mapAddressString + "'");

        String addressBody = mapAddressString.substring(1, mapAddressString.length() - 1).trim();
        if (addressBody.isEmpty()) return;

        for (String elementString : addressBody.split(",")) {
            String[] pair = elementString.split(":");
            if (pair.length != 2)
                throw new IllegalArgumentException("Expecting argument elements on the form dimension:label, " +
                                                   "got '" + elementString + "'");
            String dimension = pair[0].trim();
            builder.add(dimension, pair[1].trim());
        }
    }

}
