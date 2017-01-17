// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.json;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.google.common.annotations.Beta;
import com.google.common.base.Preconditions;
import com.yahoo.collections.Pair;
import com.yahoo.document.ArrayDataType;
import com.yahoo.document.CollectionDataType;
import com.yahoo.document.DataType;
import com.yahoo.document.Document;
import com.yahoo.document.DocumentId;
import com.yahoo.document.DocumentOperation;
import com.yahoo.document.DocumentPut;
import com.yahoo.document.DocumentRemove;
import com.yahoo.document.DocumentType;
import com.yahoo.document.DocumentTypeManager;
import com.yahoo.document.DocumentUpdate;
import com.yahoo.document.Field;
import com.yahoo.document.MapDataType;
import com.yahoo.document.PositionDataType;
import com.yahoo.document.TestAndSetCondition;
import com.yahoo.document.WeightedSetDataType;
import com.yahoo.document.datatypes.CollectionFieldValue;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.IntegerFieldValue;
import com.yahoo.document.datatypes.MapFieldValue;
import com.yahoo.document.datatypes.StructuredFieldValue;
import com.yahoo.document.datatypes.TensorFieldValue;
import com.yahoo.document.datatypes.WeightedSet;
import com.yahoo.document.json.TokenBuffer.Token;
import com.yahoo.document.update.FieldUpdate;
import com.yahoo.document.update.MapValueUpdate;
import com.yahoo.document.update.ValueUpdate;
import com.yahoo.tensor.MappedTensor;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import org.apache.commons.codec.binary.Base64;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Initialize Vespa documents/updates/removes from an InputStream containing a
 * valid JSON representation of a feed.
 *
 * @author <a href="mailto:steinar@yahoo-inc.com">Steinar Knutsen</a>
 * @since 5.1.25
 */
@Beta
public class JsonReader {

    private enum FieldOperation {
        ADD, REMOVE
    }

    static final String MAP_KEY = "key";
    static final String MAP_VALUE = "value";
    static final String FIELDS = "fields";
    static final String REMOVE = "remove";
    static final String UPDATE_INCREMENT = "increment";
    static final String UPDATE_DECREMENT = "decrement";
    static final String UPDATE_MULTIPLY = "multiply";
    static final String UPDATE_DIVIDE = "divide";
    static final String TENSOR_DIMENSIONS = "dimensions";
    static final String TENSOR_CELLS = "cells";
    static final String TENSOR_ADDRESS = "address";
    static final String TENSOR_VALUE = "value";

    private static final String UPDATE = "update";
    private static final String PUT = "put";
    private static final String ID = "id";
    private static final String CONDITION = "condition";
    private static final String CREATE_IF_NON_EXISTENT = "create";
    private static final String UPDATE_ASSIGN = "assign";
    private static final String UPDATE_REMOVE = "remove";
    private static final String UPDATE_MATCH = "match";
    private static final String UPDATE_ADD = "add";
    private static final String UPDATE_ELEMENT = "element";

    private final JsonParser parser;
    private TokenBuffer buffer = new TokenBuffer();
    private final DocumentTypeManager typeManager;
    private ReaderState state = ReaderState.AT_START;

    static class DocumentParseInfo {
        public DocumentId documentId;
        public Optional<Boolean> create = Optional.empty();
        Optional<String> condition = Optional.empty();
        SupportedOperation operationType = null;
    }

    enum SupportedOperation {
        PUT, UPDATE, REMOVE
    }

    enum ReaderState {
        AT_START, READING, END_OF_FEED
    }

    public JsonReader(DocumentTypeManager typeManager, InputStream input, JsonFactory parserFactory) {
        this.typeManager = typeManager;

        try {
            parser = parserFactory.createParser(input);
        } catch (IOException e) {
            state = ReaderState.END_OF_FEED;
            throw new RuntimeException(e);
        }
    }

    /**
     * Reads a single operation. The operation is not expected to be part of an array. It only reads FIELDS.
     * @param operationType the type of operation (update or put)
     * @param docIdString document ID.
     * @return the document
     */
    public DocumentOperation readSingleDocument(SupportedOperation operationType, String docIdString) {
        DocumentId docId = new DocumentId(docIdString);
        DocumentParseInfo documentParseInfo = parseToDocumentsFieldsAndInsertFieldsIntoBuffer(docId);
        documentParseInfo.operationType = operationType;
        DocumentOperation operation = createDocumentOperation(documentParseInfo);
        operation.setCondition(TestAndSetCondition.fromConditionString(documentParseInfo.condition));
        return operation;
    }

    public DocumentOperation next() {
        switch (state) {
            case AT_START:
                JsonToken t = nextToken();
                expectArrayStart(t);
                state = ReaderState.READING;
                break;
            case END_OF_FEED:
                return null;
            case READING:
                break;
        }

        Optional<DocumentParseInfo> documentParseInfo = parseDocument();

        if (! documentParseInfo.isPresent()) {
            state = ReaderState.END_OF_FEED;
            return null;
        }
        DocumentOperation operation = createDocumentOperation(documentParseInfo.get());
        operation.setCondition(TestAndSetCondition.fromConditionString(documentParseInfo.get().condition));
        return operation;
    }

    private DocumentOperation createDocumentOperation(DocumentParseInfo documentParseInfo) {
        DocumentType documentType = getDocumentTypeFromString(documentParseInfo.documentId.getDocType(), typeManager);
        final DocumentOperation documentOperation;
        try {
            switch (documentParseInfo.operationType) {
                case PUT:
                    documentOperation = new DocumentPut(new Document(documentType, documentParseInfo.documentId));
                    readPut((DocumentPut) documentOperation);
                    verifyEndState();
                    break;
                case REMOVE:
                    documentOperation = new DocumentRemove(documentParseInfo.documentId);
                    break;
                case UPDATE:
                    documentOperation = new DocumentUpdate(documentType, documentParseInfo.documentId);
                    readUpdate((DocumentUpdate) documentOperation);
                    verifyEndState();
                    break;
                default:
                    throw new IllegalStateException("Implementation out of sync with itself. This is a bug.");
            }
        } catch (JsonReaderException e) {
            throw JsonReaderException.addDocId(e, documentParseInfo.documentId);
        }
        if (documentParseInfo.create.isPresent()) {
            if (!(documentOperation instanceof DocumentUpdate)) {
                throw new RuntimeException("Could not set create flag on non update operation.");
            }
            DocumentUpdate update = (DocumentUpdate) documentOperation;
            update.setCreateIfNonExistent(documentParseInfo.create.get());
        }
        return documentOperation;
    }

    void readUpdate(DocumentUpdate next) {
        if (buffer.size() == 0) {
            bufferFields(nextToken());
        }
        populateUpdateFromBuffer(next);
    }

    void readPut(DocumentPut put) {
        if (buffer.size() == 0) {
            bufferFields(nextToken());
        }
        JsonToken t = buffer.currentToken();
        try {
            populateComposite(put.getDocument(), t);
        } catch (JsonReaderException e) {
            throw JsonReaderException.addDocId(e, put.getId());
        }
    }

    private DocumentParseInfo parseToDocumentsFieldsAndInsertFieldsIntoBuffer(DocumentId documentId) {
        long indentLevel = 0;
        DocumentParseInfo documentParseInfo = new DocumentParseInfo();
        documentParseInfo.documentId = documentId;
        while (true) {
            // we should now be at the start of a feed operation or at the end of the feed
            JsonToken t = nextToken();
            if (t == null) {
                throw new IllegalArgumentException("Could not read document, no document?");
            }
            switch (t) {
                case START_OBJECT:
                    indentLevel++;
                    break;
                case END_OBJECT:
                    indentLevel--;
                    break;
                case START_ARRAY:
                    indentLevel+=10000L;
                    break;
                case END_ARRAY:
                    indentLevel-=10000L;
                    break;
            }
            if (indentLevel == 1 && (t == JsonToken.VALUE_TRUE || t == JsonToken.VALUE_FALSE)) {
                try {
                    if (CREATE_IF_NON_EXISTENT.equals(parser.getCurrentName())) {
                        documentParseInfo.create = Optional.ofNullable(parser.getBooleanValue());
                        continue;
                    }
                } catch (IOException e) {
                    throw new RuntimeException("Got IO exception while parsing document", e);
                }
            }
            if (indentLevel == 2L && t == JsonToken.START_OBJECT) {

                try {
                    if (!FIELDS.equals(parser.getCurrentName())) {
                        continue;
                    }
                } catch (IOException e) {
                    throw new RuntimeException("Got IO exception while parsing document", e);
                }
                bufferFields(t);
                break;
            }
        }
        return documentParseInfo;
    }

    private void verifyEndState() {
        Preconditions.checkState(buffer.nesting() == 0, "Nesting not zero at end of operation");
        expectObjectEnd(buffer.currentToken());
        Preconditions.checkState(buffer.next() == null, "Dangling data at end of operation");
        Preconditions.checkState(buffer.size() == 0, "Dangling data at end of operation");
    }

    private void populateUpdateFromBuffer(DocumentUpdate update) {
        expectObjectStart(buffer.currentToken());
        int localNesting = buffer.nesting();
        JsonToken t = buffer.next();

        while (localNesting <= buffer.nesting()) {
            expectObjectStart(t);
            String fieldName = buffer.currentName();
            Field field = update.getType().getField(fieldName);
            addFieldUpdates(update, field);
            t = buffer.next();
        }
    }

    private void addFieldUpdates(DocumentUpdate update, Field field) {
        int localNesting = buffer.nesting();
        FieldUpdate fieldUpdate = FieldUpdate.create(field);

        buffer.next();
        while (localNesting <= buffer.nesting()) {
            switch (buffer.currentName()) {
            case UPDATE_REMOVE:
                createAddsOrRemoves(field, fieldUpdate, FieldOperation.REMOVE);
                break;
            case UPDATE_ADD:
                createAddsOrRemoves(field, fieldUpdate, FieldOperation.ADD);
                break;
            case UPDATE_MATCH:
                fieldUpdate.addValueUpdate(createMapUpdate(field));
                break;
            default:
                String action = buffer.currentName();
                fieldUpdate.addValueUpdate(readSingleUpdate(field.getDataType(), action));
            }
            buffer.next();
        }
        update.addFieldUpdate(fieldUpdate);
    }

    @SuppressWarnings("rawtypes")
    private ValueUpdate createMapUpdate(Field field) {
        buffer.next();
        MapValueUpdate m = (MapValueUpdate) createMapUpdate(field.getDataType(), null, null);
        buffer.next();
        // must generate the field value in parallell with the actual
        return m;

    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private ValueUpdate createMapUpdate(DataType currentLevel, FieldValue keyParent, FieldValue topLevelKey) {
        TokenBuffer.Token element = buffer.prefetchScalar(UPDATE_ELEMENT);
        if (UPDATE_ELEMENT.equals(buffer.currentName())) {
            buffer.next();
        }

        FieldValue key = keyTypeForMapUpdate(element, currentLevel);
        if (keyParent != null) {
            ((CollectionFieldValue) keyParent).add(key);
        }
        // structure is: [(match + element)*, (element + action)]
        // match will always have element, and either match or action
        if (!UPDATE_MATCH.equals(buffer.currentName())) {
            // we have reached an action...
            if (topLevelKey == null) {
                return ValueUpdate.createMap(key, readSingleUpdate(valueTypeForMapUpdate(currentLevel), buffer.currentName()));
            } else {
                return ValueUpdate.createMap(topLevelKey, readSingleUpdate(valueTypeForMapUpdate(currentLevel), buffer.currentName()));
            }
        } else {
            // next level of matching
            if (topLevelKey == null) {
                return createMapUpdate(valueTypeForMapUpdate(currentLevel), key, key);
            } else {
                return createMapUpdate(valueTypeForMapUpdate(currentLevel), key, topLevelKey);
            }
        }
    }

    private DataType valueTypeForMapUpdate(DataType parentType) {
        if (parentType instanceof WeightedSetDataType) {
            return DataType.INT;
        } else if (parentType instanceof CollectionDataType) {
            return ((CollectionDataType) parentType).getNestedType();
        } else if (parentType instanceof MapDataType) {
            return ((MapDataType) parentType).getValueType();
        } else {
            throw new UnsupportedOperationException("Unexpected parent type: " + parentType);
        }
    }

    private FieldValue keyTypeForMapUpdate(Token element, DataType expectedType) {
        FieldValue v;
        if (expectedType instanceof ArrayDataType) {
            v = new IntegerFieldValue(Integer.valueOf(element.text));
        } else if (expectedType instanceof WeightedSetDataType) {
            v = ((WeightedSetDataType) expectedType).getNestedType().createFieldValue(element.text);
        } else if (expectedType instanceof MapDataType) {
            v = ((MapDataType) expectedType).getKeyType().createFieldValue(element.text);
        } else {
            throw new IllegalArgumentException("Container type " + expectedType + " not supported for match update.");
        }
        return v;
    }

    @SuppressWarnings("rawtypes")
    private ValueUpdate readSingleUpdate(DataType expectedType, String action) {
        ValueUpdate update;

        switch (action) {
            case UPDATE_ASSIGN:
                update = (buffer.currentToken() == JsonToken.VALUE_NULL)
                        ? ValueUpdate.createClear()
                        : ValueUpdate.createAssign(readSingleValue(buffer.currentToken(), expectedType));
                break;
                // double is silly, but it's what is used internally anyway
            case UPDATE_INCREMENT:
                update = ValueUpdate.createIncrement(Double.valueOf(buffer.currentText()));
                break;
            case UPDATE_DECREMENT:
                update = ValueUpdate.createDecrement(Double.valueOf(buffer.currentText()));
                break;
            case UPDATE_MULTIPLY:
                update = ValueUpdate.createMultiply(Double.valueOf(buffer.currentText()));
                break;
            case UPDATE_DIVIDE:
                update = ValueUpdate.createDivide(Double.valueOf(buffer.currentText()));
                break;
            default:
                throw new IllegalArgumentException("Operation \"" + buffer.currentName() + "\" not implemented.");
        }
        return update;
    }

    // yes, this suppresswarnings ugliness is by intention, the code relies on
    // the contracts in the builders
    @SuppressWarnings({ "cast", "rawtypes", "unchecked" })
    private void createAddsOrRemoves(Field field, FieldUpdate update, FieldOperation op) {
        FieldValue container = field.getDataType().createFieldValue();
        FieldUpdate singleUpdate;
        int initNesting = buffer.nesting();
        JsonToken token;

        Preconditions.checkState(buffer.currentToken().isStructStart(), "Expected start of composite, got %s", buffer.currentToken());
        if (container instanceof CollectionFieldValue) {
            token = buffer.next();
            DataType valueType = ((CollectionFieldValue) container).getDataType().getNestedType();
            if (container instanceof WeightedSet) {
                // these are objects with string keys (which are the nested
                // types) and values which are the weight
                WeightedSet weightedSet = (WeightedSet) container;
                fillWeightedSetUpdate(initNesting, valueType, weightedSet);
                if (op == FieldOperation.REMOVE) {
                    singleUpdate = FieldUpdate.createRemoveAll(field, weightedSet);
                } else {
                    singleUpdate = FieldUpdate.createAddAll(field, weightedSet);

                }
            } else {
                List<FieldValue> arrayContents = new ArrayList<>();
                token = fillArrayUpdate(initNesting, token, valueType, arrayContents);
                if (token != JsonToken.END_ARRAY) {
                    throw new IllegalStateException("Expected END_ARRAY. Got '" + token + "'.");
                }
                if (op == FieldOperation.REMOVE) {
                    singleUpdate = FieldUpdate.createRemoveAll(field, arrayContents);
                } else {
                    singleUpdate = FieldUpdate.createAddAll(field, arrayContents);
                }
            }
        } else {
            throw new UnsupportedOperationException(
                    "Trying to add or remove from a field of a type the reader does not know how to handle: "
                            + container.getClass().getName());
        }
        expectCompositeEnd(buffer.currentToken());
        update.addAll(singleUpdate);
    }

    private JsonToken fillArrayUpdate(int initNesting, JsonToken initToken, DataType valueType, List<FieldValue> arrayContents) {
        JsonToken token = initToken;
        while (buffer.nesting() >= initNesting) {
            arrayContents.add(readSingleValue(token, valueType));
            token = buffer.next();
        }
        return token;
    }

    private void fillWeightedSetUpdate(int initNesting, DataType valueType, @SuppressWarnings("rawtypes") WeightedSet weightedSet) {
        iterateThroughWeightedSet(initNesting, valueType, weightedSet);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private void iterateThroughWeightedSet(int initNesting, DataType valueType, WeightedSet weightedSet) {
        while (buffer.nesting() >= initNesting) {
            // XXX the keys are defined in the spec to always be represented as strings
            FieldValue v = valueType.createFieldValue(buffer.currentName());
            weightedSet.put(v, Integer.valueOf(buffer.currentText()));
            buffer.next();
        }
    }

    // TODO populateComposite is extremely similar to add/remove, refactor
    // yes, this suppresswarnings ugliness is by intention, the code relies on the contracts in the builders
    @SuppressWarnings({ "cast", "rawtypes" })
    private void populateComposite(FieldValue parent, JsonToken token) {
        if ((token != JsonToken.START_OBJECT) && (token != JsonToken.START_ARRAY)) {
            throw new IllegalArgumentException("Expected '[' or '{'. Got '" + token + "'.");
        }
        if (parent instanceof CollectionFieldValue) {
            DataType valueType = ((CollectionFieldValue) parent).getDataType().getNestedType();
            if (parent instanceof WeightedSet) {
                fillWeightedSet(valueType, (WeightedSet) parent);
            } else {
                fillArray((CollectionFieldValue) parent, valueType);
            }
        } else if (parent instanceof MapFieldValue) {
            fillMap((MapFieldValue) parent);
        } else if (parent instanceof StructuredFieldValue) {
            fillStruct((StructuredFieldValue) parent);
        } else if (parent instanceof TensorFieldValue) {
            fillTensor((TensorFieldValue) parent);
        } else {
            throw new IllegalStateException("Has created a composite field"
                    + " value the reader does not know how to handle: "
                    + parent.getClass().getName() + " This is a bug. token = " + token);
        }
        expectCompositeEnd(buffer.currentToken());
    }

    private void expectCompositeEnd(JsonToken token) {
        Preconditions.checkState(token.isStructEnd(), "Expected end of composite, got %s", token);
    }

    private void fillStruct(StructuredFieldValue parent) {
        // do note the order of initializing initNesting and token is relevant for empty docs
        int initNesting = buffer.nesting();
        JsonToken token = buffer.next();

        while (buffer.nesting() >= initNesting) {
            Field f = getField(parent);
            try {
                FieldValue v = readSingleValue(token, f.getDataType());
                parent.setFieldValue(f, v);
                token = buffer.next();
            } catch (IllegalArgumentException e) {
                throw new JsonReaderException(f, e);
            }
        }
    }

    private Field getField(StructuredFieldValue parent) {
        Field f = parent.getField(buffer.currentName());
        if (f == null) {
            throw new NullPointerException("Could not get field \"" + buffer.currentName() +
                    "\" in the structure of type \"" + parent.getDataType().getDataTypeName() + "\".");
        }
        return f;
    }

    @SuppressWarnings({ "rawtypes", "cast", "unchecked" })
    private void fillMap(MapFieldValue parent) {
        JsonToken token = buffer.currentToken();
        int initNesting = buffer.nesting();
        expectArrayStart(token);
        token = buffer.next();
        DataType keyType = parent.getDataType().getKeyType();
        DataType valueType = parent.getDataType().getValueType();
        while (buffer.nesting() >= initNesting) {
            FieldValue key = null;
            FieldValue value = null;
            expectObjectStart(token);
            token = buffer.next();
            for (int i = 0; i < 2; ++i) {
                if (MAP_KEY.equals(buffer.currentName())) {
                    key = readSingleValue(token, keyType);
                } else if (MAP_VALUE.equals(buffer.currentName())) {
                    value = readSingleValue(token, valueType);
                }
                token = buffer.next();
            }
            Preconditions.checkState(key != null && value != null, "Missing key or value for map entry.");
            parent.put(key, value);

            expectObjectEnd(token);
            token = buffer.next(); // array end or next entry
        }
    }

    private void expectArrayStart(JsonToken token) {
        Preconditions.checkState(token == JsonToken.START_ARRAY, "Expected start of array, got %s", token);
    }

    private void expectObjectStart(JsonToken token) {
        Preconditions.checkState(token == JsonToken.START_OBJECT, "Expected start of JSON object, got %s", token);
    }

    private void expectObjectEnd(JsonToken token) {
        Preconditions.checkState(token == JsonToken.END_OBJECT, "Expected end of JSON object, got %s", token);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private void fillArray(CollectionFieldValue parent, DataType valueType) {
        int initNesting = buffer.nesting();
        expectArrayStart(buffer.currentToken());
        JsonToken token = buffer.next();
        while (buffer.nesting() >= initNesting) {
            parent.add(readSingleValue(token, valueType));
            token = buffer.next();
        }
    }

    private void fillWeightedSet(DataType valueType, @SuppressWarnings("rawtypes") WeightedSet weightedSet) {
        int initNesting = buffer.nesting();
        expectObjectStart(buffer.currentToken());
        buffer.next();
        iterateThroughWeightedSet(initNesting, valueType, weightedSet);
    }

    private void fillTensor(TensorFieldValue tensorFieldValue) {
        Tensor.Builder tensorBuilder = Tensor.Builder.of(tensorFieldValue.getDataType().getTensorType());
        expectObjectStart(buffer.currentToken());
        int initNesting = buffer.nesting();
        // read tensor cell fields and ignore everything else
        for (buffer.next(); buffer.nesting() >= initNesting; buffer.next()) {
            if (TENSOR_CELLS.equals(buffer.currentName()))
                readTensorCells(tensorBuilder);
        }
        expectObjectEnd(buffer.currentToken());
        tensorFieldValue.assign(tensorBuilder.build());
    }

    private void readTensorCells(Tensor.Builder tensorBuilder) {
        expectArrayStart(buffer.currentToken());
        int initNesting = buffer.nesting();
        for (buffer.next(); buffer.nesting() >= initNesting; buffer.next())
            readTensorCell(tensorBuilder);
        expectCompositeEnd(buffer.currentToken());
    }

    private void readTensorCell(Tensor.Builder tensorBuilder) {
        expectObjectStart(buffer.currentToken());
        int initNesting = buffer.nesting();
        double cellValue = 0.0;
        Tensor.Builder.CellBuilder cellBuilder = tensorBuilder.cell();
        for (buffer.next(); buffer.nesting() >= initNesting; buffer.next()) {
            String currentName = buffer.currentName();
            if (TENSOR_ADDRESS.equals(currentName)) {
                readTensorAddress(cellBuilder);
            } else if (TENSOR_VALUE.equals(currentName)) {
                cellValue = Double.valueOf(buffer.currentText());
            }
        }
        expectObjectEnd(buffer.currentToken());
        cellBuilder.value(cellValue);
    }

    private void readTensorAddress(MappedTensor.Builder.CellBuilder cellBuilder) {
        expectObjectStart(buffer.currentToken());
        int initNesting = buffer.nesting();
        for (buffer.next(); buffer.nesting() >= initNesting; buffer.next()) {
            String dimension = buffer.currentName();
            String label = buffer.currentText();
            cellBuilder.label(dimension, label);
        }
        expectObjectEnd(buffer.currentToken());
    }

    private FieldValue readSingleValue(JsonToken t, DataType expectedType) {
        if (t.isScalarValue()) {
            return readAtomic(expectedType);
        } else {
            FieldValue v = expectedType.createFieldValue();
            populateComposite(v, t);
            return v;
        }
    }

    private FieldValue readAtomic(DataType expectedType) {
        if (expectedType.equals(DataType.RAW)) {
            return expectedType.createFieldValue(new Base64().decode(buffer.currentText()));
        } else if (expectedType.equals(PositionDataType.INSTANCE)) {
            return PositionDataType.fromString(buffer.currentText());
        } else {
            return expectedType.createFieldValue(buffer.currentText());
        }
    }

    private void bufferFields(JsonToken current) {
        buffer.bufferObject(current, parser);
    }

    Optional<DocumentParseInfo> parseDocument() {
        // we should now be at the start of a feed operation or at the end of the feed
        JsonToken token = nextToken();
        if (token == JsonToken.END_ARRAY) {
            return Optional.empty(); // end of feed
        }
        expectObjectStart(token);

        DocumentParseInfo documentParseInfo = new DocumentParseInfo();

        while (true) {
            try {
                token = nextToken();
                if ((token == JsonToken.VALUE_TRUE || token == JsonToken.VALUE_FALSE) &&
                     CREATE_IF_NON_EXISTENT.equals(parser.getCurrentName())) {
                    documentParseInfo.create = Optional.of(token == JsonToken.VALUE_TRUE);
                    continue;
                }
                if (token == JsonToken.VALUE_STRING && CONDITION.equals(parser.getCurrentName())) {
                    documentParseInfo.condition = Optional.of(parser.getText());
                    continue;
                }
                if (token == JsonToken.START_OBJECT) {
                    try {
                        if (!FIELDS.equals(parser.getCurrentName())) {
                            throw new IllegalArgumentException("Unexpected object key: " + parser.getCurrentName());
                        }
                    } catch (IOException e) {
                        // TODO more specific wrapping
                        throw new RuntimeException(e);
                    }
                    bufferFields(token);
                    continue;
                }
                if (token == JsonToken.END_OBJECT) {
                    if (documentParseInfo.documentId == null) {
                        throw new RuntimeException("Did not find document operation");
                    }
                    return Optional.of(documentParseInfo);
                }
                if (token == JsonToken.VALUE_STRING) {
                    documentParseInfo.operationType = operationNameToOperationType(parser.getCurrentName());
                    documentParseInfo.documentId = new DocumentId(parser.getText());
                    continue;
                }
                throw new RuntimeException("Expected document start or document operation.");
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }

        }
    }

    private static SupportedOperation operationNameToOperationType(String operationName) {
        switch (operationName) {
            case PUT:
            case ID:
                return SupportedOperation.PUT;
            case REMOVE:
                return SupportedOperation.REMOVE;
            case UPDATE:
                return SupportedOperation.UPDATE;
            default:
                throw new IllegalArgumentException(
                        "Got " + operationName + " as document operation, only \"put\", " +
                                "\"remove\" and \"update\" are supported.");
        }
    }

    DocumentType readDocumentType(DocumentId docId) {
        return getDocumentTypeFromString(docId.getDocType(), typeManager);
    }

    private static DocumentType getDocumentTypeFromString(String docTypeString, DocumentTypeManager typeManager) {
        final DocumentType docType = typeManager.getDocumentType(docTypeString);
        if (docType == null) {
            throw new IllegalArgumentException(String.format("Document type %s does not exist", docTypeString));
        }
        return docType;
    }

    private JsonToken nextToken() {
        try {
            return parser.nextValue();
        } catch (IOException e) {
            // Jackson is not able to recover from structural parse errors
            state = ReaderState.END_OF_FEED;
            throw new RuntimeException(e);
        }
    }
}
