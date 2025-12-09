// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.result;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.yahoo.data.access.Inspector;
import com.yahoo.data.access.Inspectable;
import com.yahoo.data.access.Type;
import com.yahoo.data.disclosure.DataSource;
import com.yahoo.data.JsonProducer;
import com.yahoo.data.access.simple.Value;
import com.yahoo.data.access.slime.SlimeAdapter;
import com.yahoo.io.GrowableByteBuffer;
import com.yahoo.search.rendering.JsonGeneratorDataSink;
import com.yahoo.search.rendering.NonFiniteToNullDataSink;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Slime;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorDataSource;
import com.yahoo.tensor.serialization.JsonFormat;
import com.yahoo.tensor.serialization.TypedBinaryFormat;
import static com.yahoo.searchlib.rankingexpression.Reference.wrapInRankingExpression;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
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

    private static final JsonFactory jsonFactory = new JsonFactory();

    /** Whether values have been written to this (using set()) since it was constructed. */
    private boolean mutated = false;

    /** If not null: The initial source of values of this. */
    private final Inspector encodedValues;

    /** Values that are either set in this or lazily decoded from encodedValues. */
    private Map<String, Tensor> values = null;

    public FeatureData(Inspector encodedValues) {
        this.encodedValues = Objects.requireNonNull(encodedValues);
    }

    /** Creates a feature data from a map of values. */
    public FeatureData(Map<String, Tensor> values) {
        this.encodedValues = null;
        this.values = new LinkedHashMap<>(values);
    }

    public static FeatureData empty() { return new FeatureData(Value.empty()); }

    /**
     * Returns the fields of this as an inspector, where tensors are represented as binary data
     * which can be decoded using
     * <code>com.yahoo.tensor.serialization.TypedBinaryFormat.decode(Optional.empty(), GrowableByteBuffer.wrap(featureValue.asData()))</code>
     */
    @Override
    public Inspector inspect() {
        if (isEmpty()) return Value.empty();

        // We may have cached values in values, but unless we have changed values we can still use the inspector
        if (!mutated) return encodedValues;

        decodeAll();
        Slime slime = new Slime();
        Cursor root = slime.setObject();
        for (var entry : values.entrySet()) {
            if (entry.getValue().type().rank() == 0)
                root.setDouble(entry.getKey(), entry.getValue().asDouble());
            else
                root.setData(entry.getKey(), TypedBinaryFormat.encode(entry.getValue()));
        }
        return new SlimeAdapter(root);
    }

    @Override
    public String toJson() {
        return toJson(new JsonFormat.EncodeOptions());
    }

    public String toJson(boolean tensorShortForm) {
        return toJson(new JsonFormat.EncodeOptions(tensorShortForm));
    }

    public String toJson(boolean tensorShortForm, boolean tensorDirectValues) {
        return toJson(new JsonFormat.EncodeOptions(tensorShortForm, tensorDirectValues));
    }

    public String toJson(JsonFormat.EncodeOptions tensorOptions) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             JsonGenerator generator = jsonFactory.createGenerator(out)) {
            asDataSource(tensorOptions).emit(new NonFiniteToNullDataSink(new JsonGeneratorDataSink(generator)));
            generator.flush();
            return out.toString();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public DataSource asDataSource(JsonFormat.EncodeOptions tensorOptions) {
        return sink -> {
            if (isEmpty()) {
                sink.startObject();
                sink.endObject();
                return;
            }

            sink.startObject();
            if (encodedValues != null && !mutated) {
                encodedValues.traverse((String name, Inspector value) -> {
                    sink.fieldName(name);
                    if (value.type() == Type.DOUBLE) {
                        sink.doubleValue(value.asDouble());
                    } else if (value.type() == Type.DATA) {
                        Tensor tensor = tensorFromData(value.asData());
                        new TensorDataSource(tensor, tensorOptions).emit(sink);
                    } else {
                        throw new IllegalStateException("Unexpected feature value type " + value.type());
                    }
                });
            } else {
                decodeAll();
                for (var entry : values.entrySet()) {
                    sink.fieldName(entry.getKey());
                    if (entry.getValue().type().rank() == 0) {
                        sink.doubleValue(entry.getValue().asDouble());
                    } else {
                        new TensorDataSource(entry.getValue(), tensorOptions).emit(sink);
                    }
                }
            }
            sink.endObject();
        };
    }

    @Override
    public StringBuilder writeJson(StringBuilder target) {
        return target.append(toJson());
    }

    private void decodeAll() {
        if (encodedValues == null) return;
        encodedValues.traverse((String name, Inspector value) -> {
            if ( ! values.containsKey(name))
                values.put(name, decodeTensor(value));
        });
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
            values = new LinkedHashMap<>();

        Tensor value = values.get(featureName);
        if (value != null) return value;

        if (encodedValues != null)
            value = decodeTensor(featureName);
        if (value != null)
            values.put(featureName, value);
        return value;
    }

    /** Sets a new or modified value in this. */
    public void set(String featureName, Tensor value) {
        mutated = true;
        if (values == null)
            values = new LinkedHashMap<>();
        values.put(featureName, value);
    }

    /** Sets a new or modified value in this. */
    public void set(String featureName, double value) {
        set(featureName, Tensor.from(value));
    }

    public boolean isEmpty() {
        return (encodedValues == null || encodedValues.type() == Type.EMPTY) &&
               (values == null || values.isEmpty());
    }

    private Tensor decodeTensor(String featureName) {
        return decodeTensor(getInspector(featureName));
    }

    private Tensor decodeTensor(Inspector featureValue) {
        if ( ! featureValue.valid()) return null;

        return switch (featureValue.type()) {
            case DOUBLE -> Tensor.from(featureValue.asDouble());
            case DATA -> tensorFromData(featureValue.asData());
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
    // TODO: Not used by us - deprecate?
    public Set<String> featureNames() {
        if (isEmpty()) return Set.of();
        Set<String> featureNames = new LinkedHashSet<>();
        if (encodedValues != null)
            encodedValues.fields().forEach(field -> featureNames.add(field.getKey()));
        if (values != null)
            featureNames.addAll(values.keySet());
        return featureNames;
    }

    @Override
    public String toString() {
        if (isEmpty()) return "";
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

    private static Tensor tensorFromData(byte[] value) {
        return TypedBinaryFormat.decode(Optional.empty(), GrowableByteBuffer.wrap(value));
    }

}
