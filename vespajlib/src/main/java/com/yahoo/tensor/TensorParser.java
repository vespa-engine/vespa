// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor;

import java.util.Optional;

/**
 * @author bratseth
 */
class TensorParser {

    static Tensor tensorFrom(String tensorString, Optional<TensorType> type) {
        tensorString = tensorString.trim();
        try {
            if (tensorString.startsWith("tensor")) {
                int colonIndex = tensorString.indexOf(':');
                String typeString = tensorString.substring(0, colonIndex);
                String valueString = tensorString.substring(colonIndex + 1);
                TensorType typeFromString = TensorTypeParser.fromSpec(typeString);
                if (type.isPresent() && ! type.get().equals(typeFromString))
                    throw new IllegalArgumentException("Got tensor with type string '" + typeString + "', but was " +
                                                       "passed type " + type);
                return tensorFromValueString(valueString, typeFromString);
            }
            else if (tensorString.startsWith("{")) {
                return tensorFromValueString(tensorString, type.orElse(typeFromValueString(tensorString)));
            }
            else {
                if (type.isPresent() && ! type.get().equals(TensorType.empty))
                    throw new IllegalArgumentException("Got zero-dimensional tensor '" + tensorString +
                                                       "' where type " + type.get() + " is required");
                return Tensor.Builder.of(TensorType.empty).cell(Double.parseDouble(tensorString)).build();
            }
        }
        catch (NumberFormatException e) {
            throw new IllegalArgumentException("Excepted a number or a string starting by { or tensor(, got '" +
                                               tensorString + "'");
        }
    }

    /** Derive the tensor type from the first address string in the given tensor string */
    private static TensorType typeFromValueString(String s) {
        s = s.substring(1).trim(); // remove tensor start
        int firstKeyOrTensorEnd = s.indexOf('}');
        String addressBody = s.substring(0, firstKeyOrTensorEnd).trim();
        if (addressBody.isEmpty()) return TensorType.empty; // Empty tensor
        if ( ! addressBody.startsWith("{")) return TensorType.empty; // Single value tensor

        addressBody = addressBody.substring(1); // remove key start
        if (addressBody.isEmpty()) return TensorType.empty; // Empty key

        TensorType.Builder builder = new TensorType.Builder();
        for (String elementString : addressBody.split(",")) {
            String[] pair = elementString.split(":");
            if (pair.length != 2)
                throw new IllegalArgumentException("Expecting argument elements to be on the form dimension:label, " +
                                                   "got '" + elementString + "'");
            builder.mapped(pair[0].trim());
        }

        return builder.build();
    }

    private static Tensor tensorFromValueString(String tensorValueString, TensorType type) {
        Tensor.Builder builder = Tensor.Builder.of(type);
        tensorValueString = tensorValueString.trim();
        try {
            if (tensorValueString.startsWith("{"))
                return fromCellString(builder, tensorValueString);
            else
                return builder.cell(Double.parseDouble(tensorValueString)).build();
        }
        catch (NumberFormatException e) {
            throw new IllegalArgumentException("Excepted a number or a string starting by { or tensor(, got '" +
                                               tensorValueString + "'");
        }
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
            Double value = asDouble(address, s.substring(index, valueEnd).trim());
            builder.cell(address, value);
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

    private static Double asDouble(TensorAddress address, String s) {
        try {
            return Double.valueOf(s);
        }
        catch (NumberFormatException e) {
            throw new IllegalArgumentException("At " + address + ": Expected a floating point number, got '" + s + "'");
        }
    }

}
