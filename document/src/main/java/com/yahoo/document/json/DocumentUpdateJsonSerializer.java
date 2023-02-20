// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.json;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.yahoo.document.DataType;
import com.yahoo.document.Document;
import com.yahoo.document.DocumentUpdate;
import com.yahoo.document.FieldPath;
import com.yahoo.document.annotation.AnnotationReference;
import com.yahoo.document.datatypes.Array;
import com.yahoo.document.datatypes.BoolFieldValue;
import com.yahoo.document.datatypes.ByteFieldValue;
import com.yahoo.document.datatypes.CollectionFieldValue;
import com.yahoo.document.datatypes.DoubleFieldValue;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.FloatFieldValue;
import com.yahoo.document.datatypes.IntegerFieldValue;
import com.yahoo.document.datatypes.LongFieldValue;
import com.yahoo.document.datatypes.MapFieldValue;
import com.yahoo.document.datatypes.PredicateFieldValue;
import com.yahoo.document.datatypes.Raw;
import com.yahoo.document.datatypes.ReferenceFieldValue;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.document.datatypes.Struct;
import com.yahoo.document.datatypes.StructuredFieldValue;
import com.yahoo.document.datatypes.TensorFieldValue;
import com.yahoo.document.datatypes.WeightedSet;
import com.yahoo.document.fieldpathupdate.AddFieldPathUpdate;
import com.yahoo.document.fieldpathupdate.AssignFieldPathUpdate;
import com.yahoo.document.fieldpathupdate.FieldPathUpdate;
import com.yahoo.document.fieldpathupdate.RemoveFieldPathUpdate;
import com.yahoo.document.json.readers.SingleValueReader;
import com.yahoo.document.serialization.DocumentUpdateWriter;
import com.yahoo.document.serialization.FieldWriter;
import com.yahoo.document.update.AddValueUpdate;
import com.yahoo.document.update.ArithmeticValueUpdate;
import com.yahoo.document.update.AssignValueUpdate;
import com.yahoo.document.update.ClearValueUpdate;
import com.yahoo.document.update.FieldUpdate;
import com.yahoo.document.update.MapValueUpdate;
import com.yahoo.document.update.RemoveValueUpdate;
import com.yahoo.document.update.TensorAddUpdate;
import com.yahoo.document.update.TensorModifyUpdate;
import com.yahoo.document.update.TensorRemoveUpdate;
import com.yahoo.document.update.ValueUpdate;
import com.yahoo.vespa.objects.FieldBase;
import com.yahoo.vespa.objects.Serializer;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.stream.Collectors;

import static com.yahoo.document.json.JsonSerializationHelper.*;

/**
 * The DocumentUpdateJsonSerializer utility class is used to serialize a DocumentUpdate instance using the JSON format described in
 * <a href="https://docs.vespa.ai/en/reference/document-json-format.html#update">Document JSON Format: The Update Structure</a>
 *
 * @see #serialize(com.yahoo.document.DocumentUpdate)
 * @author Vegard Sjonfjell
 */
public class DocumentUpdateJsonSerializer {

    private final JsonFactory jsonFactory = new JsonFactory();
    private final JsonDocumentUpdateWriter writer = new JsonDocumentUpdateWriter();
    private JsonGenerator generator;

    /**
     * Instantiate a DocumentUpdateJsonSerializer that outputs JSON to an OutputStream
     */
    public DocumentUpdateJsonSerializer(OutputStream outputStream) {
        wrapIOException(() -> generator = jsonFactory.createGenerator(outputStream));
    }

    /**
     * Instantiate a DocumentUpdateJsonSerializer that writes JSON using existing JsonGenerator
     */
    public DocumentUpdateJsonSerializer(JsonGenerator generator) {
        this.generator = generator;
    }

    /**
     * Serialize a DocumentUpdate tree to JSON
     */
    public void serialize(DocumentUpdate update) {
        writer.write(update);
    }

    private class JsonDocumentUpdateWriter implements DocumentUpdateWriter, FieldWriter {
        @Override
        public void write(DocumentUpdate update) {
            wrapIOException(() -> {
                generator.writeStartObject();
                generator.writeStringField("update", update.getId().toString());

                if (update.getCondition().isPresent()) {
                    generator.writeStringField("condition", update.getCondition().getSelection());
                }

                Optional<Boolean> createIfNotExistent = update.getOptionalCreateIfNonExistent();
                if (createIfNotExistent.isPresent() && createIfNotExistent.get()) {
                    generator.writeBooleanField("create", createIfNotExistent.get());
                }

                generator.writeObjectFieldStart("fields");
                for (FieldUpdate up : update.fieldUpdates()) {
                    up.serialize(this);
                }

                update.fieldPathUpdates().stream()
                        .collect(Collectors.groupingBy(FieldPathUpdate::getFieldPath))
                        .forEach((fieldPath, fieldPathUpdates) ->
                                wrapIOException(() -> write(fieldPath, fieldPathUpdates, generator)));
                generator.writeEndObject();

                generator.writeEndObject();
                generator.flush();
            });
        }

        private void write(FieldPath fieldPath, Collection<FieldPathUpdate> fieldPathUpdates, JsonGenerator generator) throws IOException {
            generator.writeObjectFieldStart(fieldPath.toString());

            for (FieldPathUpdate update : fieldPathUpdates) {
                if (writeArithmeticFieldPathUpdate(update, generator)) continue;
                generator.writeFieldName(update.getUpdateType().name().toLowerCase());

                if (update instanceof AssignFieldPathUpdate) {
                    AssignFieldPathUpdate assignUp = (AssignFieldPathUpdate) update;
                    if (assignUp.getExpression() != null) {
                        throw new RuntimeException("Unable to parse expression: " + assignUp.getExpression());
                    } else {
                        assignUp.getNewValue().serialize(null, this);
                    }
                } else if (update instanceof AddFieldPathUpdate) {
                    ((AddFieldPathUpdate) update).getNewValues().serialize(null, this);
                } else if (update instanceof RemoveFieldPathUpdate) {
                    generator.writeNumber(0);
                } else {
                    throw new RuntimeException("Unsupported fieldpath operation: " + update.getClass().getName());
                }
            }
            generator.writeEndObject();
        }

        // Returns true if fieldpath update was an arithmetic operation after writing it to the generator
        private boolean writeArithmeticFieldPathUpdate(FieldPathUpdate fieldPathUpdate, JsonGenerator generator) throws IOException {
            if (! (fieldPathUpdate instanceof AssignFieldPathUpdate)) return false;
            String expression = ((AssignFieldPathUpdate) fieldPathUpdate).getExpression();
            if (expression == null) return false;

            Matcher matcher = SingleValueReader.matchArithmeticOperation(expression);
            if (matcher.find()) {
                String updateOperation = SingleValueReader.ARITHMETIC_SIGN_TO_UPDATE_OPERATION.get(matcher.group(1));
                double value = Double.valueOf(matcher.group(2));

                generator.writeNumberField(updateOperation, value);
                return true;
            }

            return false;
        }

        @Override
        public void write(FieldUpdate fieldUpdate) {
            wrapIOException(() -> {
                generator.writeObjectFieldStart(fieldUpdate.getField().getName());

                ArrayList<ValueUpdate> removeValueUpdates = new ArrayList<>();
                ArrayList<ValueUpdate> addValueUpdates = new ArrayList<>();

                final DataType dataType = fieldUpdate.getField().getDataType();
                for (ValueUpdate valueUpdate : fieldUpdate.getValueUpdates()) {
                    if (valueUpdate instanceof RemoveValueUpdate) {
                        removeValueUpdates.add(valueUpdate);
                    } else if (valueUpdate instanceof AddValueUpdate) {
                        addValueUpdates.add(valueUpdate);
                    } else {
                        valueUpdate.serialize(this, dataType);
                    }
                }

                writeAddOrRemoveValueUpdates("remove", removeValueUpdates, dataType);
                writeAddOrRemoveValueUpdates("add", addValueUpdates, dataType);

                generator.writeEndObject();
            });
        }

        private void writeAddOrRemoveValueUpdates(String arrayFieldName, ArrayList<ValueUpdate> valueUpdates, DataType dataType) throws IOException {
            if (!valueUpdates.isEmpty()) {
                generator.writeArrayFieldStart(arrayFieldName);
                for (ValueUpdate valueUpdate : valueUpdates) {
                    valueUpdate.serialize(this, dataType);
                }
                generator.writeEndArray();
            }
        }

        @Override
        public void write(AddValueUpdate update, DataType superType) {
            update.getValue().serialize(this);
        }

        /* This is the 'match' operation */
        @Override
        public void write(MapValueUpdate update, DataType superType) {
            wrapIOException(() -> {
                generator.writeObjectFieldStart("match");
                generator.writeFieldName("element");
                update.getValue().serialize(null, this);
                update.getUpdate().serialize(this, superType);
                generator.writeEndObject();
            });
        }

        @Override
        public void write(ArithmeticValueUpdate update) {
            final ArithmeticValueUpdate.Operator operator = update.getOperator();
            final String operationKey;

            switch (operator) {
                case ADD:
                    operationKey = "increment";
                    break;
                case DIV:
                    operationKey = "divide";
                    break;
                case MUL:
                    operationKey = "multiply";
                    break;
                case SUB:
                    operationKey = "decrement";
                    break;
                default:
                    throw new RuntimeException("Unrecognized arithmetic operator '%s'".formatted(operator.name));
            }

            wrapIOException(() -> generator.writeFieldName(operationKey));
            update.getValue().serialize(this);
        }

        @Override
        public void write(AssignValueUpdate update, DataType superType) {
            wrapIOException(() -> generator.writeFieldName("assign"));
            update.getValue().serialize(null, this);
        }

        @Override
        public void write(RemoveValueUpdate update, DataType superType) {
            update.getValue().serialize(null, this);
        }

        @Override
        public void write(ClearValueUpdate clearValueUpdate, DataType superType) {
            wrapIOException(() -> generator.writeNullField("assign"));
        }

        @Override
        public void write(TensorModifyUpdate update) {
            wrapIOException(() -> {
                generator.writeObjectFieldStart("modify");
                generator.writeFieldName("operation");
                generator.writeString(update.getOperation().name);
                if (update.getValue().getTensor().isPresent()) {
                    serializeTensorCells(generator, update.getValue().getTensor().get());
                }
                generator.writeEndObject();
            });
        }

        @Override
        public void write(TensorAddUpdate update) {
            wrapIOException(() -> {
                generator.writeObjectFieldStart("add");
                if (update.getValue().getTensor().isPresent()) {
                    serializeTensorCells(generator, update.getValue().getTensor().get());
                }
                generator.writeEndObject();
            });
        }

        @Override
        public void write(TensorRemoveUpdate update) {
            wrapIOException(() -> {
                generator.writeObjectFieldStart("remove");
                if (update.getValue().getTensor().isPresent()) {
                    serializeTensorAddresses(generator, update.getValue().getTensor().get());
                }
                generator.writeEndObject();
            });
        }

        @Override
        public void write(FieldBase field, FieldValue value) {
            throw new JsonSerializationException(String.format("Serialization of field values of type %s is not supported", value.getClass().getName()));
        }

        @Override
        public void write(FieldBase field, Document value) {
            throw new JsonSerializationException("Serialization of 'Document fields' is not supported");
        }

        @Override
        public <T extends FieldValue> void write(FieldBase field, Array<T> array) {
            serializeArrayField(this, generator, field, array);
        }

        @Override
        public <K extends FieldValue, V extends FieldValue> void write(FieldBase field, MapFieldValue<K, V> map) {
            serializeMapField(this, generator, field, map);
        }

        @Override
        public void write(FieldBase field, ByteFieldValue value) {
            serializeByteField(generator, field, value);
        }

        @Override
        public void write(FieldBase field, BoolFieldValue value) {
            serializeBoolField(generator, field, value);
        }

        @Override
        public <T extends FieldValue> void write(FieldBase field, CollectionFieldValue<T> value) {
            serializeCollectionField(this, generator, field, value);
        }

        @Override
        public void write(FieldBase field, DoubleFieldValue value) {
            serializeDoubleField(generator, field, value);
        }

        @Override
        public void write(FieldBase field, FloatFieldValue value) {
            serializeFloatField(generator, field, value);
        }

        @Override
        public void write(FieldBase field, IntegerFieldValue value) {
            serializeIntField(generator, field, value);
        }

        @Override
        public void write(FieldBase field, LongFieldValue value) {
            serializeLongField(generator, field, value);
        }

        @Override
        public void write(FieldBase field, Raw value) {
            serializeRawField(generator, field, value);
        }

        @Override
        public void write(FieldBase field, PredicateFieldValue value) {
            serializePredicateField(generator, field, value);
        }

        @Override
        public void write(FieldBase field, StringFieldValue value) {
            serializeStringField(generator, field, value);
        }

        @Override
        public void write(FieldBase field, TensorFieldValue value) {
            serializeTensorField(generator, field, value, false, false);
        }

        @Override
        public void write(FieldBase field, ReferenceFieldValue value) {
            serializeReferenceField(generator, field, value);
        }

        @Override
        public void write(FieldBase field, Struct value) {
            serializeStructField(this, generator, field, value);
        }

        @Override
        public void write(FieldBase field, StructuredFieldValue value) {
            serializeStructuredField(this, generator, field, value);
        }

        @Override
        public <T extends FieldValue> void write(FieldBase field, WeightedSet<T> weightedSet) {
            serializeWeightedSet(generator, field, weightedSet);
        }

        @Override
        public void write(FieldBase field, AnnotationReference value) {
            // Serialization of annotations are not implemented
        }

        @Override
        public Serializer putByte(FieldBase field, byte value) {
            serializeByte(generator, field, value);
            return this;
        }

        @Override
        public Serializer putShort(FieldBase field, short value) {
            serializeShort(generator, field, value);
            return this;
        }

        @Override
        public Serializer putInt(FieldBase field, int value) {
            serializeInt(generator, field, value);
            return this;
        }

        @Override
        public Serializer putLong(FieldBase field, long value) {
            serializeLong(generator, field, value);
            return this;
        }

        @Override
        public Serializer putFloat(FieldBase field, float value) {
            serializeFloat(generator, field, value);
            return this;
        }

        @Override
        public Serializer putDouble(FieldBase field, double value) {
            serializeDouble(generator, field, value);
            return this;
        }

        @Override
        public Serializer put(FieldBase field, byte[] value) {
            serializeByteArray(generator, field, value);
            return this;
        }

        @Override
        public Serializer put(FieldBase field, ByteBuffer value) {
            serializeByteBuffer(generator, field, value);
            return this;
        }

        @Override
        public Serializer put(FieldBase field, String value) {
            serializeString(generator, field, value);
            return this;
        }
    }
}
