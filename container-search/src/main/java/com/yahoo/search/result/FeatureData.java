// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.result;

import com.yahoo.data.access.Inspector;
import com.yahoo.data.access.Inspectable;
import com.yahoo.data.access.Type;
import com.yahoo.data.JsonProducer;
import com.yahoo.data.access.simple.JsonRender;
import com.yahoo.data.access.simple.Value;
import com.yahoo.io.GrowableByteBuffer;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.serialization.JsonFormat;
import com.yahoo.tensor.serialization.TypedBinaryFormat;
import static com.yahoo.searchlib.rankingexpression.Reference.wrapInRankingExpression;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * A wrapper for structured data representing feature values: A map of floats and tensors.
 * This class is immutable but not thread safe.
 *
 * @author bratseth
 */
public class FeatureData implements Inspectable, JsonProducer {

    // WARNING: Not thread safe but using a shared empty. Take care if adding mutating methods.
    private static final FeatureData empty = new FeatureData(Value.empty());

    /** If not null: The source of all the values of this. */
    private final Inspector encodedValues;

    /** If encodedValues is null: The content of this. If encodedValues is non-null: Lazily decoded values. */
    private Map<String, Tensor> values = null;

    /** The lazily computed feature names of this */
    private Set<String> featureNames = null;

    /** The lazily computed json form of this */
    private String jsonForm = null;

    public FeatureData(Inspector encodedValues) {
        this.encodedValues = Objects.requireNonNull(encodedValues);
    }

    /** Creates a feature data from a map of values. This transfers ownership of the map to this object. */
    public FeatureData(Map<String, Tensor> values) {
        this.encodedValues = null;
        this.values = values;
    }

    public static FeatureData empty() { return empty; }

    /**
     * Returns the fields of this as an inspector, where tensors are represented as binary data
     * which can be decoded using
     * <code>com.yahoo.tensor.serialization.TypedBinaryFormat.decode(Optional.empty(), GrowableByteBuffer.wrap(featureValue.asData()))</code>
     */
    @Override
    public Inspector inspect() {
        if (encodedValues == null)
            throw new IllegalStateException("FeatureData not created from an inspector cannot be inspected");
        return encodedValues;
    }

    @Override
    public String toJson() {
        return toJson(false, false);
    }

    public String toJson(boolean tensorShortForm) {
        return toJson(tensorShortForm, false);
    }

    public String toJson(boolean tensorShortForm, boolean tensorDirectValues) {
        return writeJson(tensorShortForm, tensorDirectValues, new StringBuilder()).toString();
    }

    @Override
    public StringBuilder writeJson(StringBuilder target) {
        return JsonRender.render(encodedValues, new Encoder(target, true, false, false));
    }

    private StringBuilder writeJson(boolean tensorShortForm, boolean tensorDirectValues, StringBuilder target) {
        if (this == empty) return target.append("{}");
        if (jsonForm != null) return target.append(jsonForm);

        if (encodedValues != null)
            return JsonRender.render(encodedValues, new Encoder(target, true, tensorShortForm, tensorDirectValues));
        else
            return writeJson(values, tensorShortForm, tensorDirectValues, target);
    }

    private StringBuilder writeJson(Map<String, Tensor> values, boolean tensorShortForm, boolean tensorDirectValues, StringBuilder target) {
        target.append("{");
        for (Map.Entry<String, Tensor> entry : values.entrySet()) {
            target.append("\"").append(entry.getKey()).append("\":");
            if (entry.getValue().type().rank() == 0) {
                target.append(entry.getValue().asDouble());
            } else {
                byte[] encodedTensor = JsonFormat.encode(entry.getValue(), tensorShortForm, tensorDirectValues);
                target.append(new String(encodedTensor, StandardCharsets.UTF_8));
            }
            target.append(",");
        }
        if (!values.isEmpty()) target.setLength(target.length() - 1); // remove last comma
        target.append("}");
        return target;
    }

    /**
     * Returns the value of a scalar feature, or null if it is not present.
     *
     * @throws IllegalArgumentException if the value exists but isn't a scalar
     *                                  (that is, if it is a tensor with nonzero rank)
     */
    public Double getDouble(String featureName) {
        Tensor value = getTensor(featureName);
        return value == null ? null : value.asDouble();
    }

    /**
     * Returns the value of a tensor feature, or null if it is not present.
     * This will return any feature value: Scalars are returned as a rank 0 tensor.
     */
    public Tensor getTensor(String featureName) {
        if (values == null)
            values = new HashMap<>();

        Tensor value = values.get(featureName);
        if (value != null) return value;

        if (encodedValues != null)
            value = decodeTensor(featureName);
        if (value != null)
            values.put(featureName, value);
        return value;
    }

    private Tensor decodeTensor(String featureName) {
        Inspector featureValue = getInspector(featureName);
        if ( ! featureValue.valid()) return null;

        return switch (featureValue.type()) {
            case DOUBLE -> Tensor.from(featureValue.asDouble());
            case DATA -> TypedBinaryFormat.decode(Optional.empty(), GrowableByteBuffer.wrap(featureValue.asData()));
            default -> throw new IllegalStateException("Unexpected feature value type " + featureValue.type());
        };
    }

    private Inspector getInspector(String featureName) {
        Inspector featureValue = encodedValues.field(featureName);
        if (featureValue.valid()) return featureValue;

        // Try to wrap by rankingExpression(name)
        return encodedValues.field(wrapInRankingExpression(featureName));
    }

    /** Returns the names of the features available in this */
    public Set<String> featureNames() {
        if (this == empty) return Set.of();
        if (featureNames != null) return featureNames;
        if (encodedValues == null) return values.keySet();

        featureNames = new LinkedHashSet<>();
        encodedValues.fields().forEach(field -> featureNames.add(field.getKey()));
        return featureNames;
    }

    @Override
    public String toString() {
        if (encodedValues != null && encodedValues.type() == Type.EMPTY) return "";
        return toJson();
    }

    @Override
    public int hashCode() { return toJson().hashCode(); }

    @Override
    public boolean equals(Object other) {
        if (other == this) return true;
        if ( ! (other instanceof FeatureData)) return false;
        return ((FeatureData)other).toJson().equals(this.toJson());
    }

    /** A JSON encoder which encodes DATA as a tensor */
    private static class Encoder extends JsonRender.StringEncoder {

        private final boolean tensorShortForm;
        private final boolean tensorDirectValues;

        Encoder(StringBuilder out, boolean compact, boolean tensorShortForm, boolean tensorDirectValues) {
            super(out, compact);
            this.tensorShortForm = tensorShortForm;
            this.tensorDirectValues = tensorDirectValues;
        }

        @Override
        public void encodeDATA(byte[] value) {
            // This could be done more efficiently ...
            Tensor tensor = TypedBinaryFormat.decode(Optional.empty(), GrowableByteBuffer.wrap(value));
            byte[] encodedTensor = JsonFormat.encode(tensor, tensorShortForm, tensorDirectValues);
            target().append(new String(encodedTensor, StandardCharsets.UTF_8));
        }

    }

}
