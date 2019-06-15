// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.result;

import com.yahoo.data.access.Inspector;
import com.yahoo.data.access.Inspectable;
import com.yahoo.data.access.Type;
import com.yahoo.data.JsonProducer;
import com.yahoo.data.access.simple.JsonRender;
import com.yahoo.io.GrowableByteBuffer;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.serialization.JsonFormat;
import com.yahoo.tensor.serialization.TypedBinaryFormat;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * A wrapper for structured data representing feature values: A map of floats and tensors.
 * This class is not thread safe even when it is only consumed.
 */
public class FeatureData implements Inspectable, JsonProducer {

    private final Inspector value;

    private Set<String> featureNames = null;

    public FeatureData(Inspector value) {
        this.value = value;
    }

    /**
     * Returns the fields of this as an inspector, where tensors are represented as binary data
     * which can be decoded using
     * <code>com.yahoo.tensor.serialization.TypedBinaryFormat.decode(Optional.empty(), GrowableByteBuffer.wrap(featureValue.asData()))</code>
     */
    @Override
    public Inspector inspect() { return value; }

    @Override
    public String toString() {
        if (value.type() == Type.EMPTY) return "";
        return toJson();
    }

    @Override
    public String toJson() {
        return writeJson(new StringBuilder()).toString();
    }

    @Override
    public StringBuilder writeJson(StringBuilder target) {
        return JsonRender.render(value, new Encoder(target, true));
    }

    /**
     * Returns the value of a scalar feature, or null if it is not present.
     *
     * @throws IllegalArgumentException if the value exists but isn't a scalar
     *                                  (that is, if it is a tensor with nonzero rank)
     */
    public Double getDouble(String featureName) {
        Inspector featureValue = value.field(featureName);
        if ( ! featureValue.valid()) return null;

        switch (featureValue.type()) {
            case DOUBLE: return featureValue.asDouble();
            case DATA: throw new IllegalArgumentException("Feature '" + featureName + "' is a tensor, not a double");
            default: throw new IllegalStateException("Unexpected feature value type " + featureValue.type());
        }
    }

    /**
     * Returns the value of a tensor feature, or null if it is not present.
     * This will return any feature value: Scalars are returned as a rank 0 tensor.
     */
    public Tensor getTensor(String featureName) {
        Inspector featureValue = value.field(featureName);
        if ( ! featureValue.valid()) return null;

        switch (featureValue.type()) {
            case DOUBLE: return Tensor.from(featureValue.asDouble());
            case DATA: return TypedBinaryFormat.decode(Optional.empty(), GrowableByteBuffer.wrap(featureValue.asData()));
            default: throw new IllegalStateException("Unexpected feature value type " + featureValue.type());
        }
    }

    /** Returns the names of the features available in this */
    public Set<String> featureNames() {
        if (featureNames != null) return featureNames;

        featureNames = new HashSet<>();
        value.fields().forEach(field -> featureNames.add(field.getKey()));
        return featureNames;
    }

    /** A JSON encoder which encodes DATA as a tensor */
    private static class Encoder extends JsonRender.StringEncoder {

        Encoder(StringBuilder out, boolean compact) {
            super(out, compact);
        }

        @Override
        public void encodeDATA(byte[] value) {
            // This could be done more efficiently ...
            target().append(new String(JsonFormat.encodeWithType(TypedBinaryFormat.decode(Optional.empty(), GrowableByteBuffer.wrap(value))),
                                       StandardCharsets.UTF_8));
        }

    }

}
