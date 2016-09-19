package com.yahoo.document.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.yahoo.document.Field;
import com.yahoo.document.PositionDataType;
import com.yahoo.document.datatypes.Array;
import com.yahoo.document.datatypes.CollectionFieldValue;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.MapFieldValue;
import com.yahoo.document.datatypes.PredicateFieldValue;
import com.yahoo.document.datatypes.Struct;
import com.yahoo.document.datatypes.StructuredFieldValue;
import com.yahoo.document.datatypes.TensorFieldValue;
import com.yahoo.document.datatypes.WeightedSet;
import com.yahoo.document.serialization.FieldWriter;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorAddress;
import com.yahoo.vespa.objects.FieldBase;
import org.apache.commons.codec.binary.Base64;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * @author Steinar Knutsen
 * @author Vegard Sjonfjell
 */
public class JsonSerializationHelper {
    private final static Base64 base64Encoder = new Base64();

    static class JsonSerializationException extends RuntimeException {
        public JsonSerializationException(Exception base) {
            super(base);
        }

        public JsonSerializationException(String message) {
            super(message);
        }
    }

    @FunctionalInterface
    static interface SubroutineThrowingIOException {
        void invoke() throws IOException;
    }

    static void wrapIOException(SubroutineThrowingIOException lambda) {
        try {
            lambda.invoke();
        } catch (IOException e) {
            throw new JsonSerializationException(e);
        }
    }

    public static void serializeTensorField(JsonGenerator generator, FieldBase field, TensorFieldValue value) {
        wrapIOException(() -> {
            fieldNameIfNotNull(generator, field);
            generator.writeStartObject();

            if (value.getTensor().isPresent()) {
                Tensor tensor = value.getTensor().get();
                serializeTensorDimensions(generator, tensor.dimensions());
                serializeTensorCells(generator, tensor.cells());
            }
            generator.writeEndObject();
        });
    }

    private static void serializeTensorDimensions(JsonGenerator generator, Set<String> dimensions) throws IOException {
        generator.writeArrayFieldStart(JsonReader.TENSOR_DIMENSIONS);
        for (String dimension : dimensions) {
            generator.writeString(dimension);
        }

        generator.writeEndArray();
    }

    private static void serializeTensorCells(JsonGenerator generator, Map<TensorAddress, Double> cells) throws IOException {
        generator.writeArrayFieldStart(JsonReader.TENSOR_CELLS);
        for (Map.Entry<TensorAddress, Double> cell : cells.entrySet()) {
            generator.writeStartObject();
            serializeTensorAddress(generator, cell.getKey());
            generator.writeNumberField(JsonReader.TENSOR_VALUE, cell.getValue());
            generator.writeEndObject();
        }

        generator.writeEndArray();
    }

    private static void serializeTensorAddress(JsonGenerator generator, TensorAddress address) throws IOException {
        generator.writeObjectFieldStart(JsonReader.TENSOR_ADDRESS);
        for (TensorAddress.Element element : address.elements()) {
            generator.writeStringField(element.dimension(), element.label());
        }

        generator.writeEndObject();
    }


    public static void serializeString(JsonGenerator generator, FieldBase field, String value) {
        if (value.length() == 0) {
            return;
        }

        fieldNameIfNotNull(generator, field);
        wrapIOException(() -> generator.writeString(value));
    }

    public static void serializeStructuredField(FieldWriter fieldWriter, JsonGenerator generator, FieldBase field, StructuredFieldValue value) {
        fieldNameIfNotNull(generator, field);

        wrapIOException(() -> {
            generator.writeStartObject();
            Iterator<Map.Entry<Field, FieldValue>> i = value.iterator();

            while (i.hasNext()) {
                Map.Entry<Field, FieldValue> entry = i.next();
                entry.getValue().serialize(entry.getKey(), fieldWriter);
            }

            generator.writeEndObject();
        });
    }

    public static void serializeStructField(FieldWriter fieldWriter, JsonGenerator generator, FieldBase field, Struct value) {
        if (value.getDataType() == PositionDataType.INSTANCE) {
            serializeString(generator, field, PositionDataType.renderAsString(value));
            return;
        }

        serializeStructuredField(fieldWriter, generator, field, value);
    }

    public static <T extends FieldValue> void serializeWeightedSet(JsonGenerator generator, FieldBase field, WeightedSet<T> value) {
        fieldNameIfNotNull(generator, field);

        wrapIOException(() -> {
            generator.writeStartObject();

            for (T key : value.keySet()) {
                Integer weight = value.get(key);
                // key.toString() is according to spec
                generator.writeNumberField(key.toString(), weight);
            }

            generator.writeEndObject();
        });
    }

    public static <T extends FieldValue> void serializeCollectionField(FieldWriter fieldWriter, JsonGenerator generator, FieldBase field, CollectionFieldValue<T> value) {
        fieldNameIfNotNull(generator, field);

        wrapIOException(() -> {
            generator.writeStartArray();
            Iterator<T> i = value.iterator();

            while (i.hasNext()) {
                i.next().serialize(null, fieldWriter);
            }

            generator.writeEndArray();
        });
    }


    public static <K extends FieldValue, V extends FieldValue> void serializeMapField(FieldWriter fieldWriter, JsonGenerator generator, FieldBase field, MapFieldValue<K, V> map) {
        fieldNameIfNotNull(generator, field);
        wrapIOException(() -> {
            generator.writeStartArray();

            for (Map.Entry<K, V> entry : map.entrySet()) {
                generator.writeStartObject();
                generator.writeFieldName(JsonReader.MAP_KEY);
                entry.getKey().serialize(null, fieldWriter);
                generator.writeFieldName(JsonReader.MAP_VALUE);
                entry.getValue().serialize(null, fieldWriter);
                generator.writeEndObject();
            }

            generator.writeEndArray();
        });
    }

    public static <T extends FieldValue> void serializeArrayField(FieldWriter fieldWriter, JsonGenerator generator, FieldBase field, Array<T> value) {
        wrapIOException(() -> {
            fieldNameIfNotNull(generator, field);
            generator.writeStartArray();

            for (T elem : value) {
                elem.serialize(null, fieldWriter);
            }

            generator.writeEndArray();
        });
    }

    public static void serializeDouble(JsonGenerator generator, FieldBase field, double value) {
        fieldNameIfNotNull(generator, field);
        wrapIOException(() -> generator.writeNumber(value));
    }

    public static void serializeFloat(JsonGenerator generator, FieldBase field, float value) {
        fieldNameIfNotNull(generator, field);
        wrapIOException(() -> generator.writeNumber(value));
    }

    public static void serializeInt(JsonGenerator generator, FieldBase field, int value) {
        fieldNameIfNotNull(generator, field);
        wrapIOException(() -> generator.writeNumber(value));
    }

    public static void serializeLong(JsonGenerator generator, FieldBase field, long value) {
        fieldNameIfNotNull(generator, field);
        wrapIOException(() -> generator.writeNumber(value));
    }

    public static void serializeByte(JsonGenerator generator, FieldBase field, byte value) {
        fieldNameIfNotNull(generator, field);
        wrapIOException(() -> generator.writeNumber(value));
    }

    public static void serializePredicateField(JsonGenerator generator, FieldBase field, PredicateFieldValue value){
        serializeString(generator, field, value.toString());
    }

    public static void fieldNameIfNotNull(JsonGenerator generator, FieldBase field) {
        if (field != null) {
            wrapIOException(() -> generator.writeFieldName(field.getName()));
        }
    }

    public static void serializeByteBuffer(JsonGenerator generator, FieldBase field, ByteBuffer raw) {
        final byte[] data = new byte[raw.remaining()];
        final int origPosition = raw.position();

        fieldNameIfNotNull(generator, field);

        // base64encoder has no encode methods with offset and
        // limit, so no use trying to get at the backing array if
        // available anyway
        raw.get(data);
        raw.position(origPosition);

        wrapIOException(() -> generator.writeString(base64Encoder.encodeToString(data)));
    }
}
