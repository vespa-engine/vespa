// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor;

import java.util.List;
import java.util.Optional;

/**
 * @author bratseth
 */
class TensorParser {

    static Tensor tensorFrom(String tensorString, Optional<TensorType> explicitType) {
        try {
            return tensorFromBody(tensorString, explicitType);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Could not parse '" + tensorString + "' as a tensor" +
                                               (explicitType.isPresent() ? " of type " + explicitType.get() : ""),
                                               e);
        }
    }

    static Tensor tensorFromBody(String tensorString, Optional<TensorType> explicitType) {
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
        if (valueString.startsWith("{") &&
            (type.isEmpty() || type.get().rank() == 0 || valueString.substring(1).trim().startsWith("{") || valueString.substring(1).trim().equals("}"))) {
            return tensorFromSparseValueString(valueString, type);
        }
        else if (valueString.startsWith("{")) {
            return tensorFromMixedValueString(valueString, type);
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
            throw new IllegalArgumentException("Excepted a number or a string starting by '{', '[' or 'tensor(...):...'");
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
            SparseValueParser parser = new SparseValueParser(valueString, builder);
            parser.parse();
            return builder.build();
        }
        catch (NumberFormatException e) {
            throw new IllegalArgumentException("Excepted a number or a string starting by '{' or 'tensor('");
        }
    }

    private static Tensor tensorFromMixedValueString(String valueString, Optional<TensorType> type) {
        if (type.isEmpty())
            throw new IllegalArgumentException("The mixed tensor form requires an explicit tensor type " +
                                               "on the form 'tensor(dimensions):...");
        if (type.get().dimensions().stream().filter(d -> ! d.isIndexed()).count() != 1)
            throw new IllegalArgumentException("The mixed tensor form requires a type with a single mapped dimension, " +
                                               "but got " + type.get());


        try {
            valueString = valueString.trim();
            if ( ! valueString.startsWith("{") && valueString.endsWith("}"))
                throw new IllegalArgumentException("A mixed tensor must be enclosed in {}");
            // TODO: Check if there is also at least one bound indexed dimension
            MixedTensor.BoundBuilder builder = (MixedTensor.BoundBuilder)Tensor.Builder.of(type.get());
            MixedValueParser parser = new MixedValueParser(valueString, builder);
            parser.parse();
            return builder.build();
        }
        catch (NumberFormatException e) {
            throw new IllegalArgumentException("Excepted a number or a string starting by '{' or 'tensor('");
        }
    }

    private static Tensor tensorFromDenseValueString(String valueString, Optional<TensorType> type) {
        if (type.isEmpty())
            throw new IllegalArgumentException("The dense tensor form requires an explicit tensor type " +
                                               "on the form 'tensor(dimensions):...");
        if (type.get().dimensions().stream().anyMatch(d -> (d.size().isEmpty())))
            throw new IllegalArgumentException("The dense tensor form requires a tensor type containing " +
                                               "only dense dimensions with a given size");

        IndexedTensor.BoundBuilder builder = (IndexedTensor.BoundBuilder) IndexedTensor.Builder.of(type.get());
        new DenseValueParser(valueString, builder).parse();
        return builder.build();
    }

    private static abstract class ValueParser {

        protected final String string;
        protected int position = 0;

        protected ValueParser(String string) {
            this.string = string;
        }

        protected void skipSpace() {
            while (position < string.length() && string.charAt(position) == ' ')
                position++;
        }

        protected void consume(char character) {
            skipSpace();

            if (position >= string.length())
                throw new IllegalArgumentException("At position " + position + ": Expected a '" + character +
                                                   "' but got the end of the string");
            if ( string.charAt(position) != character)
                throw new IllegalArgumentException("At position " + position + ": Expected a '" + character +
                                                   "' but got '" + string.charAt(position) + "'");
            position++;
        }

    }

    /** A single-use dense tensor string parser */
    private static class DenseValueParser extends ValueParser {

        private final IndexedTensor.DirectIndexBuilder builder;
        private final IndexedTensor.Indexes indexes;
        private final boolean hasInnerStructure;

        private long tensorIndex = 0;

        public DenseValueParser(String string, IndexedTensor.DirectIndexBuilder builder) {
            super(string);
            this.builder = builder;
            indexes = IndexedTensor.Indexes.of(builder.type());
            hasInnerStructure = hasInnerStructure(string);
        }

        public void parse() {
            if (!hasInnerStructure)
                consume('[');

            while (indexes.hasNext()) {
                indexes.next();
                for (int i = 0; i < indexes.rightDimensionsAtStart() && hasInnerStructure; i++)
                    consume('[');
                consumeNumber();
                for (int i = 0; i < indexes.rightDimensionsAtEnd() && hasInnerStructure; i++)
                    consume(']');
                if (indexes.hasNext())
                    consume(',');
            }

            if (!hasInnerStructure)
                consume(']');
        }

        public int position() { return position; }

        /** Are there inner square brackets in this or is it just a flat list of numbers until ']'? */
        private static boolean hasInnerStructure(String valueString) {
            valueString = valueString.trim();
            valueString = valueString.substring(1);
            int firstLeftBracket = valueString.indexOf('[');
            return firstLeftBracket >= 0 && firstLeftBracket < valueString.indexOf(']');
        }

        protected void consumeNumber() {
            skipSpace();

            int nextNumberEnd = nextStopCharIndex(position, string);
            TensorType.Value cellValueType = builder.type().valueType();
            String cellValueString = string.substring(position, nextNumberEnd);
            try {
                if (cellValueType == TensorType.Value.DOUBLE)
                    builder.cellByDirectIndex(tensorIndex++, Double.parseDouble(cellValueString));
                else if (cellValueType == TensorType.Value.FLOAT)
                    builder.cellByDirectIndex(tensorIndex++, Float.parseFloat(cellValueString));
                else
                    throw new IllegalArgumentException(cellValueType + " is not supported");
            }
            catch (NumberFormatException e) {
                throw new IllegalArgumentException("At position " + position + ": '" +
                                                   cellValueString + "' is not a valid " + cellValueType);
            }
            position = nextNumberEnd;
        }

        private int nextStopCharIndex(int position, String valueString) {
            while (position < valueString.length()) {
                if (valueString.charAt(position) == ',') return position;
                if (valueString.charAt(position) == ']') return position;
                position++;
            }
            throw new IllegalArgumentException("Malformed tensor value '" + valueString +
                                               "': Expected a ',', ']' or '}' after position " + position);
        }

    }

    private static class MixedValueParser extends ValueParser {

        private final MixedTensor.BoundBuilder builder;

        public MixedValueParser(String string, MixedTensor.BoundBuilder builder) {
            super(string);
            this.builder = builder;
        }

        private void parse() {
            TensorType.Dimension sparseDimension = builder.type().dimensions().stream().filter(d -> ! d.isIndexed()).findAny().get();
            TensorType sparseSubtype = MixedTensor.createPartialType(builder.type().valueType(), List.of(sparseDimension));

            skipSpace();
            consume('{');
            skipSpace();
            while (position + 1 < string.length()) {
                int labelEnd = string.indexOf(':', position);
                if (labelEnd <= position)
                    throw new IllegalArgumentException("A mixed tensor value must be on the form {sparse-label:[dense subspace], ...} ");
                String label = string.substring(position, labelEnd);
                position = labelEnd + 1;
                skipSpace();

                TensorAddress sparseAddress = new TensorAddress.Builder(sparseSubtype).add(sparseDimension.name(), label).build();
                parseDenseSubspace(sparseAddress);
                if ( ! consumeOptional(','))
                    consume('}');
                skipSpace();
            }
        }

        private void parseDenseSubspace(TensorAddress sparseAddress) {
            DenseValueParser denseParser = new DenseValueParser(string.substring(position), builder.denseSubspaceBuilder(sparseAddress));
            denseParser.parse();
            position+= denseParser.position();
        }

        private boolean consumeOptional(char character) {
            skipSpace();

            if (position >= string.length())
                return false;
            if ( string.charAt(position) != character)
                return false;

            position++;
            return true;
        }

    }

    private static class SparseValueParser extends ValueParser {

        private final Tensor.Builder builder;

        public SparseValueParser(String string, Tensor.Builder builder) {
            super(string);
            this.builder = builder;
        }

        private void parse() {
            consume('{');
            skipSpace();
            while (position + 1 < string.length()) {
                int keyOrTensorEnd = string.indexOf('}', position);
                TensorAddress.Builder addressBuilder = new TensorAddress.Builder(builder.type());
                if (keyOrTensorEnd < string.length() - 1) { // Key end: This has a key - otherwise TensorAddress is empty
                    addLabels(string.substring(position, keyOrTensorEnd + 1), addressBuilder);
                    position = keyOrTensorEnd + 1;
                    skipSpace();
                    consume(':');
                }
                int valueEnd = string.indexOf(',', position);
                if (valueEnd < 0) { // last value
                    valueEnd = string.indexOf('}', position);
                    if (valueEnd < 0)
                        throw new IllegalArgumentException("A sparse tensor string must end by '}'");
                }

                TensorAddress address = addressBuilder.build();
                TensorType.Value cellValueType = builder.type().valueType();
                String cellValueString = string.substring(position, valueEnd).trim();
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

                position = valueEnd+1;
                skipSpace();
            }
        }

        /** Creates a tensor address from a string on the form {dimension1:label1,dimension2:label2,...} */
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

}
