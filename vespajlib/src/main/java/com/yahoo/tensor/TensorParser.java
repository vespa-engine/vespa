// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.yahoo.tensor.serialization.JsonFormat.decodeHexString;

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

        // The order in which dimensions are written in the type string.
        // This allows the user's explicit dimension order to decide what (dense) dimensions map to what, rather than
        // the natural order of the tensor.
        List<String> dimensionOrder;

        tensorString = tensorString.trim();
        if (tensorString.startsWith("tensor")) {
            int colonIndex = tensorString.indexOf(':');
            String typeString = tensorString.substring(0, colonIndex);
            dimensionOrder = new ArrayList<>();
            TensorType typeFromString = TensorTypeParser.fromSpec(typeString, dimensionOrder);
            if (explicitType.isPresent() && ! explicitType.get().equals(typeFromString))
                throw new IllegalArgumentException("Got tensor with type string '" + typeString + "', but was " +
                                                   "passed type " + explicitType.get());
            type = Optional.of(typeFromString);
            valueString = tensorString.substring(colonIndex + 1);
        }
        else {
            type = explicitType;
            valueString = tensorString;
            dimensionOrder = null;
        }

        valueString = valueString.trim();
        if (valueString.startsWith("{") &&
            (type.isEmpty() || type.get().rank() == 0 || valueString.substring(1).trim().startsWith("{") || valueString.substring(1).trim().equals("}"))) {
            return tensorFromMappedValueString(valueString, type);
        }
        else if (valueString.startsWith("{")) {
            return tensorFromMixedValueString(valueString, type, dimensionOrder);
        }
        else if (valueString.startsWith("[")) {
            return tensorFromDenseValueString(valueString, type, dimensionOrder);
        }
        else {
            var t = maybeFromBinaryValueString(valueString, type, dimensionOrder);
            if (t.isPresent()) { return t.get(); }

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
    private static TensorType typeFromMappedValueString(String valueString) {
        TensorType.Builder builder = new TensorType.Builder();
        MappedValueTypeParser parser = new MappedValueTypeParser(valueString, builder);
        parser.parse();
        return builder.build();
    }

    private static Tensor tensorFromMappedValueString(String valueString, Optional<TensorType> type) {
        try {
            valueString = valueString.trim();
            Tensor.Builder builder = Tensor.Builder.of(type.orElse(typeFromMappedValueString(valueString)));
            MappedValueParser parser = new MappedValueParser(valueString, builder);
            parser.parse();
            return builder.build();
        }
        catch (NumberFormatException e) {
            throw new IllegalArgumentException("Excepted a number or a string starting by '{' or 'tensor('");
        }
    }

    private static Tensor tensorFromMixedValueString(String valueString,
                                                     Optional<TensorType> type,
                                                     List<String> dimensionOrder) {
        if (type.isEmpty())
            throw new IllegalArgumentException("The mixed tensor form requires an explicit tensor type " +
                                               "on the form 'tensor(dimensions):...");
        if (type.get().dimensions().stream().filter(d -> ! d.isIndexed()).count() > 1)
            throw new IllegalArgumentException("The mixed tensor form requires a type with a single mapped dimension, " +
                                               "but got " + type.get());


        try {
            valueString = valueString.trim();
            if ( ! valueString.startsWith("{") && valueString.endsWith("}"))
                throw new IllegalArgumentException("A mixed tensor must be enclosed in {}");
            Tensor.Builder builder = Tensor.Builder.of(type.get());
            MixedValueParser parser = new MixedValueParser(valueString, dimensionOrder, builder);
            parser.parse();
            return builder.build();
        }
        catch (NumberFormatException e) {
            throw new IllegalArgumentException("Excepted a number or a string starting by '{' or 'tensor('");
        }
    }

    private static Optional<Tensor> maybeFromBinaryValueString(
            String valueString,
            Optional<TensorType> optType,
            List<String> dimensionOrder)
    {
        if (optType.isEmpty() || dimensionOrder != null) {
            return Optional.empty();
        }
        var type = optType.get();
        long sz = 1;
        for (var d : type.dimensions()) {
                sz *= d.size().orElse(0L);
        }
        if (sz == 0
            || type.dimensions().size() == 0
            || valueString.length() < sz * 2
            || valueString.chars().anyMatch(ch -> (Character.digit(ch, 16) == -1)))
        {
            return Optional.empty();
        }
        try {
            double[] values = decodeHexString(valueString, type.valueType());
            if (values.length != sz) {
                return Optional.empty();
            }
            var builder = IndexedTensor.Builder.of(type);
            var dib = (IndexedTensor.DirectIndexBuilder) builder;
            for (int i = 0; i < sz; ++i) {
                dib.cellByDirectIndex(i, values[i]);
            }
            return Optional.of(builder.build());
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static Tensor tensorFromDenseValueString(String valueString,
                                                     Optional<TensorType> type,
                                                     List<String> dimensionOrder) {
        if (type.isEmpty())
            throw new IllegalArgumentException("The dense tensor form requires an explicit tensor type " +
                                               "on the form 'tensor(dimensions):...");

        IndexedTensor.Builder builder = IndexedTensor.Builder.of(type.get());

        if (type.get().dimensions().stream().anyMatch(d -> (d.size().isEmpty()))) {
            new UnboundDenseValueParser(valueString, builder).parse();
            return checkBoundDimensionSizes(builder.build());
        }

        new DenseValueParser(valueString, dimensionOrder, (IndexedTensor.BoundBuilder) builder).parse();
        return builder.build();
    }

    private static Tensor checkBoundDimensionSizes(IndexedTensor tensor) {
        TensorType type = tensor.type();
        for (int i = 0; i < type.dimensions().size(); ++i) {
            TensorType.Dimension dimension = type.dimensions().get(i);
            if (dimension.size().isPresent() && dimension.size().get() != tensor.dimensionSizes().size(i)) {
                throw new IllegalArgumentException("Unexpected size " + tensor.dimensionSizes().size(i) +
                        " for dimension " + dimension.name() + " for type " + type);
            }
        }
        return tensor;
    }

    private static abstract class ValueParser {

        protected final String string;
        protected int position = 0;

        protected ValueParser(String string) {
            this.string = string;
        }

        protected void skipSpace() {
            while (position < string.length() && Character.isWhitespace(string.charAt(position)))
                position++;
        }

        protected void consume(char character) {
            skipSpace();

            if (position >= string.length())
                throw new IllegalArgumentException("At value position " + position + ": Expected a '" + character +
                                                   "' but got the end of the string");
            if ( string.charAt(position) != character)
                throw new IllegalArgumentException("At value position " + position + ": Expected a '" + character +
                                                   "' but got '" + string.charAt(position) + "'");
            position++;
        }

        protected String consumeIdentifier() {
            int endIdentifier = nextStopCharIndex(position, string);
            String identifier = string.substring(position, endIdentifier);
            position = endIdentifier;
            return identifier;
        }

        protected String consumeLabel() {
            if (consumeOptional('\'')) {
                int endQuote = string.indexOf('\'', position);
                if (endQuote < 0)
                    throw new IllegalArgumentException("At value position " + position +
                                                       ": A label quoted by a tick (') must end by another tick");
                String label = string.substring(position, endQuote);
                position = endQuote + 1;
                return label;
            }
            else if (consumeOptional('"')) {
                int endQuote = string.indexOf('"', position);
                if (endQuote < 0)
                    throw new IllegalArgumentException("At value position " + position +
                                                       ": A label quoted by a double quote (\") must end by another double quote");
                String label = string.substring(position, endQuote);
                position = endQuote + 1;
                return label;
            }
            else {
                return consumeIdentifier();
            }
        }

        protected Number consumeNumber(TensorType.Value cellValueType) {
            skipSpace();

            int nextNumberEnd = nextStopCharIndex(position, string);
            try {
                String cellValueString = string.substring(position, nextNumberEnd);
                try {
                    switch (cellValueType) {
                        case DOUBLE:   return Double.parseDouble(cellValueString);
                        case FLOAT:    return Float.parseFloat(cellValueString);
                        case BFLOAT16: return Float.parseFloat(cellValueString);
                        case INT8:     return Float.parseFloat(cellValueString);
                        default:
                            throw new IllegalArgumentException(cellValueType + " is not supported");
                    }
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("At value position " + position + ": '" +
                                                       cellValueString + "' is not a valid " + cellValueType);
                }
            }
            finally {
                position = nextNumberEnd;
            }
        }

        protected boolean consumeOptional(char character) {
            skipSpace();

            if (position >= string.length())
                return false;
            if ( string.charAt(position) != character)
                return false;

            position++;
            return true;
        }

        protected int nextStopCharIndex(int position, String valueString) {
            while (position < valueString.length()) {
                if (Character.isWhitespace(valueString.charAt(position))) return position;
                if (valueString.charAt(position) == ',') return position;
                if (valueString.charAt(position) == ']') return position;
                if (valueString.charAt(position) == '}') return position;
                if (valueString.charAt(position) == ':') return position;
                position++;
            }
            throw new IllegalArgumentException("Malformed tensor string '" + valueString +
                                               "': Expected a ',', ']' or '}', ':' after position " + position);
        }

    }

    /** A single-use dense tensor string parser */
    private static class DenseValueParser extends ValueParser {

        private final IndexedTensor.DirectIndexBuilder builder;
        private final IndexedTensor.Indexes indexes;
        private final boolean hasInnerStructure;

        public DenseValueParser(String string,
                                List<String> dimensionOrder,
                                IndexedTensor.DirectIndexBuilder builder) {
            super(string);
            this.builder = builder;
            indexes = IndexedTensor.Indexes.of(builder.type(), dimensionOrder);
            hasInnerStructure = hasInnerStructure(string);
        }

        public void parse() {
            if (!hasInnerStructure)
                consume('[');

            while (indexes.hasNext()) {
                indexes.next();
                for (int i = 0; i < indexes.nextDimensionsAtStart() && hasInnerStructure; i++)
                    consume('[');
                consumeNumber();
                for (int i = 0; i < indexes.nextDimensionsAtEnd() && hasInnerStructure; i++)
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
            Number number = consumeNumber(builder.type().valueType());
            switch (builder.type().valueType()) {
                case DOUBLE:   builder.cellByDirectIndex(indexes.toSourceValueIndex(), (Double)number); break;
                case FLOAT:    builder.cellByDirectIndex(indexes.toSourceValueIndex(), (Float)number); break;
                case BFLOAT16: builder.cellByDirectIndex(indexes.toSourceValueIndex(), (Float)number); break;
                case INT8:     builder.cellByDirectIndex(indexes.toSourceValueIndex(), (Float)number); break;
            }
        }
    }

    /**
     * Parses unbound tensor short forms - e.g. tensor(x[],y[]):[[1,2,3],[4,5,6]]
     */
    private static class UnboundDenseValueParser extends ValueParser {

        private final IndexedTensor.Builder builder;
        private final long[] indexes;

        public UnboundDenseValueParser(String string, IndexedTensor.Builder builder) {
            super(string);
            this.builder = builder;
            this.indexes = new long[builder.type().dimensions().size()];
        }

        public void parse() {
            consumeList(0);
        }

        private void consumeList(int dimension) {
            consume('[');
            indexes[dimension] = 0;
            while ( ! atListEnd() ) {
                if (isInnerMostDimension(dimension)) {
                    consumeNumber();
                } else {
                    consumeList(dimension + 1);
                }
                indexes[dimension]++;
                consumeOptional(',');
            }
            consume(']');
        }

        private void consumeNumber() {
            Number number = consumeNumber(builder.type().valueType());
            switch (builder.type().valueType()) {
                case DOUBLE:   builder.cell((Double)number, indexes); break;
                case FLOAT:    builder.cell((Float)number, indexes); break;
                case BFLOAT16: builder.cell((Float)number, indexes); break;
                case INT8:     builder.cell((Float)number, indexes); break;
            }
        }

        private boolean isInnerMostDimension(int dimension) {
            return dimension == (indexes.length - 1);
        }

        protected boolean atListEnd() {
            skipSpace();
            if (position >= string.length()) {
                throw new IllegalArgumentException("At value position " + position + ": Expected a ']'" +
                        " but got the end of the string");
            }
            return string.charAt(position) == ']';
        }

    }

    /**
     * Parses mixed tensor short forms {a:[1,2], ...} AND 1d mapped tensor short form {a:b, ...}.
     */
    private static class MixedValueParser extends ValueParser {

        private final Tensor.Builder builder;
        private List<String> dimensionOrder;

        public MixedValueParser(String string, List<String> dimensionOrder, Tensor.Builder builder) {
            super(string);
            this.dimensionOrder = dimensionOrder;
            this.builder = builder;
        }

        private void parse() {
            TensorType.Dimension mappedDimension = findMappedDimension();
            TensorType mappedSubtype = MixedTensor.createPartialType(builder.type().valueType(), List.of(mappedDimension));
            if (dimensionOrder != null)
                dimensionOrder.remove(mappedDimension.name());

            skipSpace();
            consume('{');
            skipSpace();
            while (position + 1 < string.length()) {
                String label = consumeLabel();
                consume(':');
                TensorAddress mappedAddress = new TensorAddress.Builder(mappedSubtype).add(mappedDimension.name(), label).build();
                if (builder.type().rank() > 1)
                    parseDenseSubspace(mappedAddress, dimensionOrder);
                else
                    consumeNumber(mappedAddress);
                if ( ! consumeOptional(','))
                    consume('}');
                skipSpace();
            }
        }

        private TensorType.Dimension findMappedDimension() {
            Optional<TensorType.Dimension> mappedDimension = builder.type().dimensions().stream().filter(d -> d.isMapped()).findAny();
            if (mappedDimension.isPresent()) return mappedDimension.get();
            if (builder.type().rank() == 1 && builder.type().dimensions().get(0).size().isEmpty())
                return builder.type().dimensions().get(0);
            throw new IllegalStateException("No suitable dimension in " + builder.type() +
                                            " for parsing as a mixed tensor. This is a bug.");
        }

        private void parseDenseSubspace(TensorAddress mappedAddress, List<String> denseDimensionOrder) {
            DenseValueParser denseParser = new DenseValueParser(string.substring(position),
                                                                denseDimensionOrder,
                                                                ((MixedTensor.BoundBuilder)builder).denseSubspaceBuilder(mappedAddress));
            denseParser.parse();
            position += denseParser.position();
        }

        private void consumeNumber(TensorAddress address) {
            Number number = consumeNumber(builder.type().valueType());
            switch (builder.type().valueType()) {
                case DOUBLE:   builder.cell(address, (Double)number); break;
                case FLOAT:    builder.cell(address, (Float)number); break;
                case BFLOAT16: builder.cell(address, (Float)number); break;
                case INT8:     builder.cell(address, (Float)number); break;
            }
        }
    }

    private static class MappedValueParser extends ValueParser {

        private final Tensor.Builder builder;

        public MappedValueParser(String string, Tensor.Builder builder) {
            super(string);
            this.builder = builder;
        }

        private void parse() {
            consume('{');
            skipSpace();
            while (position + 1 < string.length()) {
                TensorAddress address = consumeLabels();
                if ( ! address.isEmpty())
                    consume(':');
                else
                    consumeOptional(':');

                int valueEnd = string.indexOf(',', position);
                if (valueEnd < 0) { // last value
                    valueEnd = string.indexOf('}', position);
                    if (valueEnd < 0)
                        throw new IllegalArgumentException("A mapped tensor string must end by '}'");
                }

                TensorType.Value cellValueType = builder.type().valueType();
                String cellValueString = string.substring(position, valueEnd).trim();
                try {
                    switch (cellValueType) {
                        case DOUBLE:   builder.cell(address, Double.parseDouble(cellValueString)); break;
                        case FLOAT:    builder.cell(address, Float.parseFloat(cellValueString)); break;
                        case BFLOAT16: builder.cell(address, Float.parseFloat(cellValueString)); break;
                        case INT8:     builder.cell(address, Float.parseFloat(cellValueString)); break;
                        default:
                            throw new IllegalArgumentException(cellValueType + " is not supported");
                    }
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
        private TensorAddress consumeLabels() {
            TensorAddress.Builder addressBuilder = new TensorAddress.Builder(builder.type());
            if ( ! consumeOptional('{')) return addressBuilder.build();
            while ( ! consumeOptional('}')) {
                String dimension = consumeIdentifier();
                consume(':');
                String label = consumeLabel();
                addressBuilder.add(dimension, label);
                consumeOptional(',');
            }
            return addressBuilder.build();
        }

    }

    /** Parses a tensor *value* into a type */
    private static class MappedValueTypeParser extends ValueParser {

        private final TensorType.Builder builder;

        public MappedValueTypeParser(String string, TensorType.Builder builder) {
            super(string);
            this.builder = builder;
        }

        /** Derives the tensor type from the first address string in the given tensor string */
        public void parse() {
            consume('{');
            consumeLabels();
        }

        /** Consumes a mapped address into a set of the type builder */
        private void consumeLabels() {
            if ( ! consumeOptional('{')) return;
            while ( ! consumeOptional('}')) {
                String dimension = consumeIdentifier();
                consume(':');
                consumeLabel();
                builder.mapped(dimension);
                consumeOptional(',');
            }
        }

    }

}
