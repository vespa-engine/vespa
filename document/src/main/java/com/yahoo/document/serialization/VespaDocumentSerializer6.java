// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.serialization;

import com.yahoo.compress.Compressor;

import com.yahoo.document.ArrayDataType;
import com.yahoo.document.CollectionDataType;
import com.yahoo.document.DataType;
import com.yahoo.document.Document;
import com.yahoo.document.DocumentId;
import com.yahoo.document.DocumentType;
import com.yahoo.document.DocumentUpdate;
import com.yahoo.document.Field;
import com.yahoo.document.WeightedSetDataType;
import com.yahoo.document.annotation.AlternateSpanList;
import com.yahoo.document.annotation.Annotation;
import com.yahoo.document.annotation.AnnotationReference;
import com.yahoo.document.annotation.Span;
import com.yahoo.document.annotation.SpanList;
import com.yahoo.document.annotation.SpanNode;
import com.yahoo.document.annotation.SpanTree;
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
import com.yahoo.document.predicate.BinaryFormat;
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
import com.yahoo.io.GrowableByteBuffer;
import com.yahoo.tensor.serialization.TypedBinaryFormat;
import com.yahoo.vespa.objects.BufferSerializer;
import com.yahoo.vespa.objects.FieldBase;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static com.yahoo.text.Utf8.calculateBytePositions;

/**
 * Class used for serializing documents on the Vespa 6.x document format.
 *
 * @author baldersheim
 **/
public class VespaDocumentSerializer6 extends BufferSerializer implements DocumentSerializer {

    private int spanNodeCounter = -1;
    private int[] bytePositions;

    VespaDocumentSerializer6(GrowableByteBuffer buf) {
        super(buf);
    }

    public void write(Document doc) {
        write(new Field(doc.getDataType().getName(), 0, doc.getDataType()), doc);
    }

    @SuppressWarnings("deprecation")
    public void write(FieldBase field, Document doc) {
        buf.putShort(Document.SERIALIZED_VERSION);

        //save the position of the length in the buffer
        int lenPos = buf.position();
        // Temporary length, fill in after serialization is done.
        buf.putInt(0);

        doc.getId().serialize(this);

        boolean hasHead = (doc.getFieldCount() != 0);

        byte contents = 0x01; // Indicating we have document type which we always have
        if (hasHead) {
            contents |= 0x2; // Indicate we have header
        }

        buf.put(contents);

        doc.getDataType().serialize(this);
        if (hasHead) {
            doc.getHeader().serialize(null, this);
        }

        int finalPos = buf.position();
        buf.position(lenPos);
        buf.putInt(finalPos - lenPos - 4); // Don't include the length itself or the version
        buf.position(finalPos);
    }

    /**
     * Write out the value of field
     *
     * @param field - field description (name and data type)
     * @param value - field value
     */
    public void write(FieldBase field, FieldValue value) {
        throw new IllegalArgumentException("Not Implemented");
    }

    /**
     * Write out the value of array field
     *
     * @param field - field description (name and data type)
     * @param array - field value
     */
    public <T extends FieldValue> void write(FieldBase field, Array<T> array) {
        buf.putInt1_2_4Bytes(array.size());

        List<T> lst = array.getValues();
        for (FieldValue value : lst) {
            value.serialize(this);
        }

    }

    public <K extends FieldValue, V extends FieldValue> void write(FieldBase field, MapFieldValue<K, V> map) {
        buf.putInt1_2_4Bytes(map.size());
        for (Map.Entry<K, V> e : map.entrySet()) {
            e.getKey().serialize(this);
            e.getValue().serialize(this);
        }
    }

    /**
     * Write out the value of byte field
     *
     * @param field - field description (name and data type)
     * @param value - field value
     */
    public void write(FieldBase field, ByteFieldValue value) {
        buf.put(value.getByte());
    }

    @Override
    public void write(FieldBase field, BoolFieldValue value) {
        byte asByte = value.getBoolean() ? (byte)1 : (byte)0;
        buf.put(asByte);
    }

    /**
     * Write out the value of collection field
     *
     * @param field - field description (name and data type)
     * @param value - field value
     */
    public <T extends FieldValue> void write(FieldBase field, CollectionFieldValue<T> value) {
        throw new IllegalArgumentException("Not Implemented");
    }

    /**
     * Write out the value of double field
     *
     * @param field - field description (name and data type)
     * @param value - field value
     */
    public void write(FieldBase field, DoubleFieldValue value) {
        buf.putDouble(value.getDouble());
    }

    /**
     * Write out the value of float field
     *
     * @param field - field description (name and data type)
     * @param value - field value
     */
    public void write(FieldBase field, FloatFieldValue value) {
        buf.putFloat(value.getFloat());
    }

    /**
     * Write out the value of integer field
     *
     * @param field - field description (name and data type)
     * @param value - field value
     */
    public void write(FieldBase field, IntegerFieldValue value) {
        buf.putInt(value.getInteger());
    }

    /**
     * Write out the value of long field
     *
     * @param field - field description (name and data type)
     * @param value - field value
     */
    public void write(FieldBase field, LongFieldValue value) {
        buf.putLong(value.getLong());
    }

    /**
     * Write out the value of raw field
     *
     * @param field - field description (name and data type)
     * @param value - field value
     */
    public void write(FieldBase field, Raw value) {
        ByteBuffer rawBuf = value.getByteBuffer();
        int origPos = rawBuf.position();
        buf.putInt(rawBuf.remaining());
        buf.put(rawBuf);
        rawBuf.position(origPos);

    }

    @Override
    public void write(FieldBase field, PredicateFieldValue value) {
        byte[] buf = BinaryFormat.encode(value.getPredicate());
        this.buf.putInt(buf.length);
        this.buf.put(buf);
    }

    /**
     * Write out the value of string field
     *
     * @param field - field description (name and data type)
     * @param value - field value
     */
    public void write(FieldBase field, StringFieldValue value) {
        byte[] stringBytes = createUTF8CharArray(value.getString());

        byte coding = 0;
        //Use bit 6 of "coding" to say whether span tree is available or not
        if (!value.getSpanTrees().isEmpty()) {
            coding |= 64;
        }
        buf.put(coding);
        buf.putInt1_4Bytes(stringBytes.length + 1);

        buf.put(stringBytes);
        buf.put(((byte) 0));

        Map<String, SpanTree> trees = value.getSpanTreeMap();
        if ((trees != null) && !trees.isEmpty()) {
            try {
                //we don't support serialization of nested span trees, so this is safe:
                bytePositions = calculateBytePositions(value.getString());
                //total length. record position and go back here if necessary:
                int posBeforeSize = buf.position();
                buf.putInt(0);
                buf.putInt1_2_4Bytes(trees.size());

                for (SpanTree tree : trees.values()) {
                    try {
                        write(tree);
                    } catch (SerializationException e) {
                        throw e;
                    } catch (RuntimeException e) {
                        throw new SerializationException("Exception thrown while serializing span tree '" +
                                                         tree.getName() + "'; string='" + value.getString() + "'", e);
                    }
                }
                int endPos = buf.position();
                buf.position(posBeforeSize);
                buf.putInt(endPos - posBeforeSize - 4); //length shall exclude itself
                buf.position(endPos);
            } finally {
                bytePositions = null;
            }
        }
    }

    @Override
    public void write(FieldBase field, TensorFieldValue value) {
        var encodedTensor = value.getSerializedTensor();
        if (encodedTensor.isPresent()) {
            buf.putInt1_4Bytes(encodedTensor.get().length);
            buf.put(encodedTensor.get());
        } else {
            buf.putInt1_4Bytes(0);
        }
    }

    @Override
    public void write(FieldBase field, ReferenceFieldValue value) {
        if (value.getDocumentId().isPresent()) {
            // We piggyback on DocumentId's existing serialization code, but need to know
            // whether or not it's present or merely the empty string.
            buf.put((byte)1);
            write(value.getDocumentId().get());
        } else {
            buf.put((byte)0);
        }
    }

    /**
     * Write out the value of struct field
     *
     * @param field - field description (name and data type)
     * @param s     - field value
     */
    public void write(FieldBase field, Struct s) {
        // Serialize all parts first.. As we need to know length before starting
        // Serialize all the fields.

        //keep the buffer we're serializing everything into:
        GrowableByteBuffer bigBuffer = buf;

        //create a new buffer and serialize into that for a while:
        GrowableByteBuffer buffer = new GrowableByteBuffer(4096, 2.0f);
        buf = buffer;

        List<Integer> fieldIds = new LinkedList<>();
        List<java.lang.Integer> fieldLengths = new LinkedList<>();

        for (Map.Entry<Field, FieldValue> value : s.getFields()) {

            int startPos = buffer.position();
            value.getValue().serialize(value.getKey(), this);

            fieldLengths.add(buffer.position() - startPos);
            fieldIds.add(value.getKey().getId());
        }

        // Switch buffers again:
        buffer.flip();
        buf = bigBuffer;

        int uncompressedSize = buffer.remaining();
        Compressor.Compression compression =
            s.getDataType().getCompressor().compress(buffer.getByteBuffer().array(), buffer.remaining());

        // Actual serialization starts here.
        int lenPos = buf.position();
        putInt(null, 0); // Move back to this after compression is done.
        buf.put(compression.type().getCode());

        if (compression.data() != null && compression.type().isCompressed()) {
            buf.putInt2_4_8Bytes(uncompressedSize);
        }

        buf.putInt1_4Bytes(s.getFieldCount());

        for (int i = 0; i < s.getFieldCount(); ++i) {
            putInt1_4Bytes(null, fieldIds.get(i));
            putInt2_4_8Bytes(null, fieldLengths.get(i));
        }

        int pos = buf.position();
        if (compression.data() != null && compression.type().isCompressed()) {
            put(null, compression.data());
        } else {
            put(null, buffer.getByteBuffer());
        }
        int dataLength = buf.position() - pos;

        int posNow = buf.position();
        buf.position(lenPos);
        putInt(null, dataLength);
        buf.position(posNow);
    }

    /**
     * Write out the value of structured field
     *
     * @param field - field description (name and data type)
     * @param value - field value
     */
    public void write(FieldBase field, StructuredFieldValue value) {
        throw new IllegalArgumentException("Not Implemented");
    }

    /**
     * Write out the value of weighted set field
     *
     * @param field - field description (name and data type)
     * @param ws    - field value
     */
    public <T extends FieldValue> void write(FieldBase field, WeightedSet<T> ws) {
        WeightedSetDataType type = ws.getDataType();
        putInt(null, type.getNestedType().getId());
        putInt(null, ws.size());

        Iterator<T> it = ws.fieldValueIterator();
        while (it.hasNext()) {
            FieldValue key = it.next();
            java.lang.Integer value = ws.get(key);
            int sizePos = buf.position();
            putInt(null, 0);
            int startPos = buf.position();
            key.serialize(this);
            putInt(null, value);
            int finalPos = buf.position();
            int size = finalPos - startPos;
            buf.position(sizePos);
            putInt(null, size);
            buf.position(finalPos);
        }

    }

    public void write(FieldBase field, AnnotationReference value) {
        int annotationId = value.getReference().getScratchId();
        if (annotationId >= 0) {
            buf.putInt1_2_4Bytes(annotationId);
        } else {
            throw new SerializationException("Could not serialize AnnotationReference value, reference not found (" + value + ")");
        }
    }

    public void write(DocumentId id) {
        put(null, id.getScheme().toUtf8().getBytes());
        putByte(null, (byte) 0);
    }

    public void write(DocumentType type) {
        byte[] docType = createUTF8CharArray(type.getName());
        put(null, docType);
        putByte(null, ((byte) 0));
        putShort(null, (short) 0); // Used to hold the version. Is now always 0.
    }

    public void write(Annotation annotation) {
        buf.putInt(annotation.getType().getId());  //name hash

        byte features = 0;
        if (annotation.isSpanNodeValid()) {
            features |= ((byte) 1);
        }
        if (annotation.hasFieldValue()) {
            features |= ((byte) 2);
        }
        buf.put(features);

        int posBeforeSize = buf.position();
        buf.putInt1_2_4BytesAs4(0);

        //write ID of span node:
        if (annotation.isSpanNodeValid()) {
            int spanNodeId = annotation.getSpanNode().getScratchId();
            if (spanNodeId >= 0) {
                buf.putInt1_2_4Bytes(spanNodeId);
            } else {
                throw new SerializationException("Could not serialize annotation, associated SpanNode not found (" + annotation + ")");
            }
        }

        //write annotation value:
        if (annotation.hasFieldValue()) {
            buf.putInt(annotation.getType().getDataType().getId());
            annotation.getFieldValue().serialize(this);
        }

        int end = buf.position();
        buf.position(posBeforeSize);
        buf.putInt1_2_4BytesAs4(end - posBeforeSize - 4);
        buf.position(end);
    }

    public void write(SpanTree tree) {
        //we don't support serialization of nested span trees:
        if (spanNodeCounter >= 0) {
            throw new SerializationException("Serialization of nested SpanTrees is not supported.");
        }

        //we're going to write a new SpanTree, create a new Map for nodes:
        spanNodeCounter = 0;

        //make sure tree is consistent before continuing:
        tree.cleanup();

        try {
            new StringFieldValue(tree.getName()).serialize(this);

            write(tree.getRoot());
            {
                //add all annotations to temporary list and sort it, to get predictable serialization
                List<Annotation> tmpAnnotationList = new ArrayList<Annotation>(tree.numAnnotations());
                for (Annotation annotation : tree) {
                    tmpAnnotationList.add(annotation);
                }
                Collections.sort(tmpAnnotationList);

                int annotationCounter = 0;
                //add all annotations to map here, in case of back-references:
                for (Annotation annotation : tmpAnnotationList) {
                    annotation.setScratchId(annotationCounter++);
                }

                buf.putInt1_2_4Bytes(tmpAnnotationList.size());
                for (Annotation annotation : tmpAnnotationList) {
                    write(annotation);
                }
            }
        } finally {
            //we're done, let's set these to null to save memory and prevent madness:
            spanNodeCounter = -1;
        }
    }

    public void write(SpanNode spanNode) {
        if (spanNodeCounter >= 0) {
            spanNode.setScratchId(spanNodeCounter++);
        }
        if (spanNode instanceof Span) {
            write((Span) spanNode);
        } else if (spanNode instanceof AlternateSpanList) {
            write((AlternateSpanList) spanNode);
        } else if (spanNode instanceof SpanList) {
            write((SpanList) spanNode);
        } else {
            throw new IllegalStateException("BUG!! Unable to serialize " + spanNode);
        }
    }

    public void write(Span span) {
        buf.put(Span.ID);

        if (bytePositions != null) {
            int byteFrom = bytePositions[span.getFrom()];
            int byteLength = bytePositions[span.getFrom() + span.getLength()] - byteFrom;

            buf.putInt1_2_4Bytes(byteFrom);
            buf.putInt1_2_4Bytes(byteLength);
        } else {
            throw new SerializationException("Cannot serialize Span " + span + ", no access to parent StringFieldValue.");
        }
    }

    public void write(SpanList spanList) {
        buf.put(SpanList.ID);
        buf.putInt1_2_4Bytes(spanList.numChildren());
        Iterator<SpanNode> children = spanList.childIterator();
        while (children.hasNext()) {
            write(children.next());
        }
    }

    public void write(AlternateSpanList altSpanList) {
        buf.put(AlternateSpanList.ID);
        buf.putInt1_2_4Bytes(altSpanList.getNumSubTrees());
        for (int i = 0; i < altSpanList.getNumSubTrees(); i++) {
            buf.putDouble(altSpanList.getProbability(i));
            buf.putInt1_2_4Bytes(altSpanList.numChildren(i));
            Iterator<SpanNode> children = altSpanList.childIterator(i);
            while (children.hasNext()) {
                write(children.next());
            }
        }
    }

    @Override
    public void write(DocumentUpdate update) {
        update.getId().serialize(this);

        update.getDocumentType().serialize(this);

        putInt(null, update.fieldUpdates().size());

        for (FieldUpdate up : update.fieldUpdates()) {
            up.serialize(this);
        }

        DocumentUpdateFlags flags = new DocumentUpdateFlags();
        flags.setCreateIfNonExistent(update.getCreateIfNonExistent());
        putInt(null, flags.injectInto(update.fieldPathUpdates().size()));

        for (FieldPathUpdate up : update.fieldPathUpdates()) {
            up.serialize(this);
        }
    }

    public void write(FieldPathUpdate update) {
        putByte(null, (byte)update.getUpdateType().getCode());
        put(null, update.getOriginalFieldPath());
        put(null, update.getOriginalWhereClause());
    }

    public void write(AssignFieldPathUpdate update) {
        write((FieldPathUpdate)update);
        byte flags = 0;
        if (update.getRemoveIfZero()) {
            flags |= AssignFieldPathUpdate.REMOVE_IF_ZERO;
        }
        if (update.getCreateMissingPath()) {
            flags |= AssignFieldPathUpdate.CREATE_MISSING_PATH;
        }
        if (update.isArithmetic()) {
            flags |= AssignFieldPathUpdate.ARITHMETIC_EXPRESSION;
            putByte(null, flags);
            put(null, update.getExpression());
        } else {
            putByte(null, flags);
            update.getFieldValue().serialize(this);
        }
    }

    public void write(AddFieldPathUpdate update) {
        write((FieldPathUpdate)update);
        update.getNewValues().serialize(this);
    }

    @Override
    public void write(FieldUpdate update) {
        putInt(null, update.getField().getId());
        putInt(null, update.getValueUpdates().size());
        for (ValueUpdate vupd : update.getValueUpdates()) {
            putInt(null, vupd.getValueUpdateClassID().id);
            vupd.serialize(this, update.getField().getDataType());
        }
    }

    @Override
    public void write(AddValueUpdate update, DataType superType) {
        writeValue(this, ((CollectionDataType)superType).getNestedType(), update.getValue());
        putInt(null, update.getWeight());
    }

    @Override
    public void write(MapValueUpdate update, DataType superType) {
        if (superType instanceof ArrayDataType) {
            CollectionDataType type = (CollectionDataType) superType;
            IntegerFieldValue index = (IntegerFieldValue) update.getValue();
            index.serialize(this);
            putInt(null, update.getUpdate().getValueUpdateClassID().id);
            update.getUpdate().serialize(this, type.getNestedType());
        } else if (superType instanceof WeightedSetDataType) {
            writeValue(this, ((CollectionDataType)superType).getNestedType(), update.getValue());
            putInt(null, update.getUpdate().getValueUpdateClassID().id);
            update.getUpdate().serialize(this, DataType.INT);
        } else {
            throw new SerializationException("MapValueUpdate only works for arrays and weighted sets");
        }
    }

    @Override
    public void write(ArithmeticValueUpdate update) {
        putInt(null, update.getOperator().id);
        putDouble(null, update.getOperand().doubleValue());
    }

    @Override
    public void write(AssignValueUpdate update, DataType superType) {
        if (update.getValue() == null) {
            putByte(null, (byte) 0);
        } else {
            putByte(null, (byte) 1);
            writeValue(this, superType, update.getValue());
        }
    }

    @Override
    public void write(RemoveValueUpdate update, DataType superType) {
        writeValue(this, ((CollectionDataType)superType).getNestedType(), update.getValue());
    }

    @Override
    public void write(ClearValueUpdate clearValueUpdate, DataType superType) {
        //TODO: This has never ever been implemented. Has this ever worked?
    }

    @Override
    public void write(TensorModifyUpdate update) {
        throw new IllegalArgumentException("Write of TensorModifyUpdate not implemented for this document format version");
    }

    @Override
    public void write(TensorAddUpdate update) {
        throw new IllegalArgumentException("Write of TensorAddUpdate not implemented for this document format version");
    }

    @Override
    public void write(TensorRemoveUpdate update) {
        throw new IllegalArgumentException("Write of TensorRemoveUpdate not implemented for this document format version");
    }


    /**
     * Returns the serialized size of the given {@link Document}. Please note that this method performs actual
     * serialization of the document, but simply return the size of the final {@link GrowableByteBuffer}. If you need
     * the buffer itself, do NOT use this method.
     *
     * @param doc The Document whose size to calculate.
     * @return The size in bytes.
     */
    public static long getSerializedSize(Document doc) {
        DocumentSerializer serializer = new VespaDocumentSerializer6(new GrowableByteBuffer());
        serializer.write(doc);
        return serializer.getBuf().position();
    }

    private static void writeValue(VespaDocumentSerializer6 serializer, DataType dataType, Object value) {
        FieldValue fieldValue;
        if (value instanceof FieldValue) {
            fieldValue = (FieldValue)value;
        } else {
            fieldValue = dataType.createFieldValue(value);
        }
        fieldValue.serialize(serializer);
    }

}
