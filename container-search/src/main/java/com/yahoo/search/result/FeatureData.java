// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
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

    private final Inspector value;

    private Set<String> featureNames = null;

    /** Cached decoded values */
    private Map<String, Double> decodedDoubles = null;
    private Map<String, Tensor> decodedTensors = null;

    private String jsonForm = null;

    public FeatureData(Inspector value) {
        this.value = value;
    }

    public static FeatureData empty() { return empty; }

    /**
     * Returns the fields of this as an inspector, where tensors are represented as binary data
     * which can be decoded using
     * <code>com.yahoo.tensor.serialization.TypedBinaryFormat.decode(Optional.empty(), GrowableByteBuffer.wrap(featureValue.asData()))</code>
     */
    @Override
    public Inspector inspect() { return value; }

    @Override
    public String toJson() {
        if (this == empty) return "{}";
        if (jsonForm != null) return jsonForm;

        jsonForm = writeJson(new StringBuilder()).toString();
        return jsonForm;
    }

    public String toJson(boolean tensorShortForm) {
        return toJson(tensorShortForm, false);
    }

    public String toJson(boolean tensorShortForm, boolean tensorDirectValues) {
        if (this == empty) return "{}";
        if (jsonForm != null) return jsonForm;

        jsonForm = JsonRender.render(value, new Encoder(new StringBuilder(), true, tensorShortForm, tensorDirectValues)).toString();
        return jsonForm;
    }

    @Override
    public StringBuilder writeJson(StringBuilder target) {
        return JsonRender.render(value, new Encoder(target, true, false, false));
    }

    /**
     * Returns the value of a scalar feature, or null if it is not present.
     *
     * @throws IllegalArgumentException if the value exists but isn't a scalar
     *                                  (that is, if it is a tensor with nonzero rank)
     */
    public Double getDouble(String featureName) {
        if (decodedDoubles == null)
            decodedDoubles = new HashMap<>();

        Double value = decodedDoubles.get(featureName);
        if (value != null) return value;

        value = decodeDouble(featureName);
        if (value != null)
            decodedDoubles.put(featureName, value);
        return value;
    }

    private Double decodeDouble(String featureName) {
        Inspector featureValue = getInspector(featureName);
        if ( ! featureValue.valid()) return null;

        return switch (featureValue.type()) {
            case DOUBLE -> featureValue.asDouble();
            case DATA -> throw new IllegalArgumentException("Feature '" + featureName + "' is a tensor, not a double");
            default -> throw new IllegalStateException("Unexpected feature value type " + featureValue.type());
        };
    }

    /**
     * Returns the value of a tensor feature, or null if it is not present.
     * This will return any feature value: Scalars are returned as a rank 0 tensor.
     */
    public Tensor getTensor(String featureName) {
        if (decodedTensors == null)
            decodedTensors = new HashMap<>();

        Tensor value = decodedTensors.get(featureName);
        if (value != null) return value;

        value = decodeTensor(featureName);
        if (value != null)
            decodedTensors.put(featureName, value);
        return value;
    }

    private Tensor decodeTensor(String featureName) {
        Inspector featureValue = getInspector(featureName);
        if ( ! featureValue.valid()) return null;

        switch (featureValue.type()) {
            case DOUBLE: return Tensor.from(featureValue.asDouble());
            case DATA: return TypedBinaryFormat.decode(Optional.empty(), GrowableByteBuffer.wrap(featureValue.asData()));
            default: throw new IllegalStateException("Unexpected feature value type " + featureValue.type());
        }
    }

    private Inspector getInspector(String featureName) {
        Inspector featureValue = value.field(featureName);
        if (featureValue.valid()) return featureValue;

        // Try to wrap by rankingExpression(name)
        return value.field("rankingExpression(" + featureName + ")");
    }

    /** Returns the names of the features available in this */
    public Set<String> featureNames() {
        if (this == empty) return Collections.emptySet();
        if (featureNames != null) return featureNames;

        featureNames = new HashSet<>();
        value.fields().forEach(field -> featureNames.add(field.getKey()));
        return featureNames;
    }

    @Override
    public String toString() {
        if (value.type() == Type.EMPTY) return "";
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
