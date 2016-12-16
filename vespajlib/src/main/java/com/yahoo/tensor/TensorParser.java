package com.yahoo.tensor;

import com.google.common.annotations.Beta;

import java.util.Optional;

/**
 * @author bratseth
 */
@Beta
class TensorParser {

    static Tensor tensorFrom(String tensorString, Optional<TensorType> type) {
        tensorString = tensorString.trim();
        try {
            if (tensorString.startsWith("tensor(")) {
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
                                                       "but type is not empty but " + type.get());
                return IndexedTensor.Builder.of(TensorType.empty).cell(Double.parseDouble(tensorString)).build();
            }
        }
        catch (NumberFormatException e) {
            throw new IllegalArgumentException("Excepted a number or a string starting by { or tensor(, got '" +
                                               tensorString + "'");
        }
    }

    private static Tensor tensorFromValueString(String tensorCellString, TensorType type) {
        boolean containsIndexedDimensions = type.dimensions().stream().anyMatch(d -> d.isIndexed());
        boolean containsMappedDimensions = type.dimensions().stream().anyMatch(d -> !d.isIndexed());
        if (containsIndexedDimensions && containsMappedDimensions)
            throw new IllegalArgumentException("Mixed dimension types are not supported, got: " + type);
        if (containsMappedDimensions)
            return MappedTensor.from(type, tensorCellString);
        else // indexed or none
            return IndexedTensor.from(type, tensorCellString);
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

}
