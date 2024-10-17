// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor;

import java.util.function.Consumer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.yahoo.tensor.serialization.JsonFormat;
import static com.yahoo.tensor.serialization.JsonFormat.decodeHexString;

/**
 * @author bratseth
 */
class TensorParser {

    static Tensor tensorFrom(String tensorString, Optional<TensorType> explicitType) {
        try {
            return tensorFromBody(tensorString, explicitType);
        } catch (IllegalArgumentException e) {
            if (explicitType.isPresent()) {
                // handle legal JSON-based tensor formats as well:
                try {
                    return JsonFormat.decode(explicitType.get(), tensorString.getBytes(UTF_8));
                } catch (RuntimeException ignored) {
                    // return error from exception above
                }
            }
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
        long numMappedDims = type.get().dimensions().stream().filter(d -> d.isMapped()).count();
        try {
            valueString = valueString.trim();
            if ( ! valueString.startsWith("{") && valueString.endsWith("}"))
                throw new IllegalArgumentException("A mixed tensor must be enclosed in {}");
            Tensor.Builder builder = Tensor.Builder.of(type.get());
            if (numMappedDims == 0) {
                if (! SingleUnboundParser.canHandle(type.get())) {
                    throw new IllegalArgumentException("No suitable dimension in " + type.get() + " for parsing a tensor on " +
                                                       "the mixed form: Should have one mapped dimension");
                }
                var parser = new SingleUnboundParser(valueString, builder);
                parser.parse();
            } else {
                var parser = new GenericMixedValueParser(valueString, dimensionOrder, builder);
                parser.parse();
            }
            return builder.build();
        }
        catch (NumberFormatException e) {
            throw new IllegalArgumentException("Excepted a number or a string starting by '{' or 'tensor('");
        }
    }

    private static boolean validHexString(TensorType type, String valueString) {
        long sz = 1;
        for (var d : type.dimensions()) {
            sz *= d.size().orElse(0L);
        }
        int numHexDigits = (int)(sz * 2 * type.valueType().sizeOfCell());
        if (sz == 0
            || type.dimensions().isEmpty()
            || valueString.length() != numHexDigits
            || valueString.chars().anyMatch(ch -> (Character.digit(ch, 16) == -1)))
        {
            return false;
        }
        return true;
    }

    private static Optional<Tensor> maybeFromBinaryValueString(
            String valueString,
            Optional<TensorType> optType,
            List<String> dimensionOrder)
    {
        if (optType.isEmpty()) {
            return Optional.empty();
        }
        var type = optType.get();
        if (validHexString(type, valueString)) {
            var tensor = tensorFromDenseValueString(valueString, optType, dimensionOrder);
            return Optional.of(tensor);
        }
        return Optional.empty();
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
            int endIdentifier = requiredNextStopCharIndex(position);
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

        protected void consumeNumber(TensorType.Value cellValueType,
                                     Consumer<Float> consumeFloat,
                                     Consumer<Double> consumeDouble) {
            skipSpace();
            int nextNumberEnd = requiredNextStopCharIndex(position);
            String cellValueString = string.substring(position, nextNumberEnd);
            try {
                switch (cellValueType) {
                    case DOUBLE -> consumeDouble.accept(Double.parseDouble(cellValueString));
                    case FLOAT, BFLOAT16, INT8 -> consumeFloat.accept(Float.parseFloat(cellValueString));
                    default -> throw new IllegalArgumentException(cellValueType + " is not supported");
                };
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("At value position " + position + ": '" +
                                                   cellValueString + "' is not a valid " + cellValueType);
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

        protected int stopCharIndex(int pos) {
            while (pos < string.length()) {
                var ch = string.charAt(pos);
                if (Character.isWhitespace(ch)) return pos;
                if (ch == ',') return pos;
                if (ch == ']') return pos;
                if (ch == '}') return pos;
                if (ch == ':') return pos;
                pos++;
            }
            return pos;
        }

        protected int requiredNextStopCharIndex(int pos) {
            pos = stopCharIndex(pos);
            if (pos == string.length()) {
                throw new IllegalArgumentException("Malformed tensor string '" + string +
                                                   "': Expected a ',', ']' or '}', ':' after position " + pos);
            }
            return pos;
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
            skipSpace();
            if (string.charAt(position) != '[') {
                int stopPos = stopCharIndex(position);
                String hexToken = string.substring(position, stopPos);
                if (validHexString(builder.type(), hexToken)) {
                    double[] values = decodeHexString(hexToken, builder.type().valueType());
                    int i = 0;
                    while (indexes.hasNext()) {
                        indexes.next();
                        builder.cellByDirectIndex(indexes.toSourceValueIndex(), values[i++]);
                    }
                    if (i != values.length) {
                        throw new IllegalStateException("consume " + i + " values out of " + values.length);
                    }
                    position = stopPos;
                    return;
                }
            }
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
            consumeNumber(builder.type().valueType(),
                          f -> builder.cellByDirectIndex(indexes.toSourceValueIndex(), f),
                          d -> builder.cellByDirectIndex(indexes.toSourceValueIndex(), d));
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
            consumeNumber(builder.type().valueType(),
                          f -> builder.cell(f, indexes),
                          d -> builder.cell(d, indexes));
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
     * Parses mixed tensor short form {0:17.0, 1:42.0, ...} used for single unbound dimension
     */
    private static class SingleUnboundParser extends ValueParser {

        private final Tensor.Builder builder;

        public SingleUnboundParser(String string, Tensor.Builder builder) {
            super(string);
            this.builder = builder;
        }

        private void parse() {
            var type = builder.type();
            String dimName = type.dimensions().get(0).name();
            skipSpace();
            consume('{');
            skipSpace();
            while (position + 1 < string.length()) {
                String label = consumeLabel();
                consume(':');
                TensorAddress mappedAddress = new TensorAddress.Builder(type).add(dimName, label).build();
                consumeNumber(mappedAddress);
                if ( ! consumeOptional(','))
                    consume('}');
                skipSpace();
            }
        }

        static boolean canHandle(TensorType type) {
            if (type.rank() != 1) return false;
            var dim = type.dimensions().get(0);
            return (dim.isIndexed() && dim.size().isEmpty());
        }

        private void consumeNumber(TensorAddress address) {
            consumeNumber(builder.type().valueType(),
                          f -> builder.cell(address, f),
                          d -> builder.cell(address, d));
        }
    }

    /**
     * Parses mixed tensor short form {a:{b1:[1,2], b2:[2,3]}, ...}
     */
    private static class GenericMixedValueParser extends ValueParser {

        private final Tensor.Builder builder;
        private final TensorType type;
        private final List<TensorType.Dimension> mappedDimensions;
        private final TensorType mappedSubtype;
        private final List<String> denseDimensionOrder;

        public GenericMixedValueParser(String string, List<String> dimensionOrder, Tensor.Builder builder) {
            super(string);
            this.builder = builder;
            this.type = builder.type();
            var allDims = findOrder(dimensionOrder, type);
            this.mappedDimensions = findMapped(allDims, type);
            this.mappedSubtype = MixedTensor.createPartialType(type.valueType(), mappedDimensions);
            this.denseDimensionOrder = new ArrayList<>(allDims);
            for (var mapped : this.mappedDimensions) {
                denseDimensionOrder.remove(mapped.name());
            }
        }

        private static final List<String> findOrder(List<String> dimensionOrder, TensorType type) {
            if (dimensionOrder == null) {
                return type.dimensions().stream().map(d -> d.name()).toList();
            } else {
                return dimensionOrder;
            }
        }

        private static final List<TensorType.Dimension> findMapped(List<String> dimensionOrder, TensorType type) {
            List<TensorType.Dimension> result = new ArrayList<>();
            for (var name : dimensionOrder) {
                var dim = type.dimension(name).orElseThrow(() -> new IllegalArgumentException("bad dimension " + name));
                if (dim.isMapped()) {
                    result.add(dim);
                }
            }
            return result;
        }

        private void parse() {
            consume('{');
            skipSpace();
            while (position + 1 < string.length()) {
                var addrBuilder = new TensorAddress.Builder(mappedSubtype);
                parseSubspace(addrBuilder, 0);
                if ( ! consumeOptional(',')) {
                    break;
                }
            }
            consume('}');
        }

        private void parseSubspace(TensorAddress.Builder addrBuilder, int level) {
            if (level >= mappedDimensions.size()) {
                throw new IllegalArgumentException("Too many nested {label:...} levels");
            }
            String label = consumeLabel();
            addrBuilder.add(mappedDimensions.get(level).name(), label);
            consume(':');
            ++level;
            if (consumeOptional('{')) {
                do {
                    parseSubspace(addrBuilder, level);
                } while (consumeOptional(','));
                consume('}');
            } else {
                if (level < mappedDimensions.size()) {
                    throw new IllegalArgumentException("Not enough nested {label:...} levels");
                }
                var mappedAddress = addrBuilder.build();
                if (builder.type().rank() > level)
                    parseDenseSubspace(mappedAddress, denseDimensionOrder);
                else
                    consumeNumber(mappedAddress);
            }
        }

        private void parseDenseSubspace(TensorAddress mappedAddress, List<String> denseDimensionOrder) {
            var subBuilder = ((MixedTensor.BoundBuilder)builder).denseSubspaceBuilder(mappedAddress);
            var rest = string.substring(position);
            DenseValueParser denseParser = new DenseValueParser(rest,
                                                                denseDimensionOrder,
                                                                subBuilder);
            denseParser.parse();
            position += denseParser.position();
        }

        private void consumeNumber(TensorAddress address) {
            consumeNumber(builder.type().valueType(),
                          f -> builder.cell(address, f),
                          d -> builder.cell(address, d));
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
            while (position < string.length()) {
                skipSpace();
                if (string.charAt(position) == '}') {
                    break;
                }
                TensorAddress address = consumeLabels();
                if ( ! address.isEmpty())
                    consume(':');
                else
                    consumeOptional(':');
                consumeNumber(builder.type().valueType(),
                              f -> builder.cell(address, f),
                              d -> builder.cell(address, d));
                if (! consumeOptional(',')) {
                    break;
                }
            }
            if (! consumeOptional('}')) {
                throw new IllegalArgumentException("A mapped tensor string must end by '}'");
            }
            skipSpace();
            if (position < string.length()) {
                throw new IllegalArgumentException("Garbage after mapped tensor string: " + string.substring(position));
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

        private void parseDenseSubspace(TensorAddress mappedAddress, List<String> denseDimensionOrder) {
            var subBuilder = ((MixedTensor.BoundBuilder)builder).denseSubspaceBuilder(mappedAddress);
            var rest = string.substring(position);
            DenseValueParser denseParser = new DenseValueParser(rest,
                                                                denseDimensionOrder,
                                                                subBuilder);
            denseParser.parse();
            position += denseParser.position();
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
