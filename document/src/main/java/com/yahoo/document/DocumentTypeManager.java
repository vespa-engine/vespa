// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document;

import com.google.inject.Inject;
import com.yahoo.config.subscription.ConfigSubscriber;
import com.yahoo.document.annotation.AnnotationReferenceDataType;
import com.yahoo.document.annotation.AnnotationType;
import com.yahoo.document.annotation.AnnotationTypeRegistry;
import com.yahoo.document.annotation.AnnotationTypes;
import com.yahoo.document.config.DocumentmanagerConfig;
import com.yahoo.document.serialization.DocumentDeserializer;
import com.yahoo.document.serialization.DocumentDeserializerFactory;
import com.yahoo.io.GrowableByteBuffer;
import com.yahoo.tensor.TensorType;

import java.lang.reflect.Modifier;
import java.util.*;
import java.util.logging.Logger;

/**
 * The DocumentTypeManager keeps track of the document types registered in
 * the Vespa common repository.
 * <p>
 * The DocumentTypeManager is also responsible for registering a FieldValue
 * factory for each data type a field can have. The Document object
 * uses this factory to serialize and deserialize the various datatypes.
 * The factory could also be used to expand the functionality of various
 * datatypes, for instance displaying the data type in human-readable form
 * or as XML.
 *
 * @author Thomas Gundersen
 */
public class DocumentTypeManager {

    private final static Logger log = Logger.getLogger(DocumentTypeManager.class.getName());
    private ConfigSubscriber subscriber;

    // *Configured data types* (not built-in/primitive) indexed by their id
    //
    // *Primitive* data types are always available and have a single id.
    //
    // *Built-in dynamic* types: The tensor type.
    // Any tensor type has the same id and is always available just like primitive types.
    // However, unlike primitive types, each tensor type is a separate DataType instance
    // (which carries additional type information (detailedType) supplied at runtime).
    // The reason for this is that we want the data type to stay the same when we change the detailed tensor type
    // to maintain compatibility on (some) tensor type changes.
    private Map<Integer, DataType> dataTypes = new LinkedHashMap<>();
    private Map<DataTypeName, DocumentType> documentTypes = new LinkedHashMap<>();
    private AnnotationTypeRegistry annotationTypeRegistry = new AnnotationTypeRegistry();

    public DocumentTypeManager() {
        registerDefaultDataTypes();
    }

    @Inject
    public DocumentTypeManager(DocumentmanagerConfig config) {
        this();
        DocumentTypeManagerConfigurer.configureNewManager(config, this);
    }

    public void assign(DocumentTypeManager other) {
        dataTypes = other.dataTypes;
        documentTypes = other.documentTypes;
        annotationTypeRegistry = other.annotationTypeRegistry;
    }

    public DocumentTypeManager configure(String configId) {
        subscriber = DocumentTypeManagerConfigurer.configure(this, configId);
        return this;
    }

    private void registerDefaultDataTypes() {
        DocumentType superDocType = DataType.DOCUMENT;
        dataTypes.put(superDocType.getId(), superDocType);
        documentTypes.put(superDocType.getDataTypeName(), superDocType);

        Class<? extends DataType> dataTypeClass = DataType.class;
        for (java.lang.reflect.Field field : dataTypeClass.getFields()) {
            if (Modifier.isStatic(field.getModifiers()) && Modifier.isFinal(field.getModifiers())) {
                if (DataType.class.isAssignableFrom(field.getType())) {
                    try {
                        //these are all static final DataTypes listed in DataType:
                        DataType type = (DataType) field.get(null);
                        register(type);
                    } catch (IllegalAccessException e) {
                        //ignore
                    }
                }
            }
        }
        for (AnnotationType type : AnnotationTypes.ALL_TYPES) {
            annotationTypeRegistry.register(type);
        }
    }

    public boolean hasDataType(String name) {
        if (name.startsWith("tensor(")) return true; // built-in dynamic: Always present
        for (DataType type : dataTypes.values()) {
            if (type.getName().equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    public boolean hasDataType(int code) {
        if (code == DataType.tensorDataTypeCode) return true; // built-in dynamic: Always present
        return dataTypes.containsKey(code);
    }

    public DataType getDataType(String name) {
        if (name.startsWith("tensor(")) // built-in dynamic
            return new TensorDataType(TensorType.fromSpec(name));

        List<DataType> foundTypes = new ArrayList<>();
        for (DataType type : dataTypes.values()) {
            if (type.getName().equalsIgnoreCase(name)) {
                foundTypes.add(type);
            }
        }
        if (foundTypes.isEmpty()) {
            throw new IllegalArgumentException("No datatype named " + name);
        } else if (foundTypes.size() == 1) {
            return foundTypes.get(0);
        } else {
            //the found types are probably documents or structs, sort them by type
            Collections.sort(foundTypes, new Comparator<DataType>() {
                public int compare(DataType first, DataType second) {
                    if (first instanceof StructuredDataType && !(second instanceof StructuredDataType)) {
                        return 1;
                    } else if (!(first instanceof StructuredDataType) && second instanceof StructuredDataType) {
                        return -1;
                    } else {
                        return 0;
                    }
                }
            });
        }
        return foundTypes.get(0);
    }

    public DataType getDataType(int code) { return getDataType(code, ""); }

    /**
     * Return a data type instance
     *
     * @param code the code of the data type to return, which must be either built in or present in this manager
     * @param detailedType detailed type information, or the empty string if none
     * @return the appropriate DataType instance
     */
    public DataType getDataType(int code, String detailedType) {
        if (code == DataType.tensorDataTypeCode) // built-in dynamic
            return new TensorDataType(TensorType.fromSpec(detailedType));

        DataType type = dataTypes.get(code);
        if (type == null) {
            StringBuilder types=new StringBuilder();
            for (Integer key : dataTypes.keySet()) {
                types.append(key).append(" ");
            }
            throw new IllegalArgumentException("No datatype with code " + code + ". Registered type ids: " + types);
        } else {
            return type;
        }
    }

    DataType getDataTypeAndReturnTemporary(int code, String detailedType) {
        if (hasDataType(code)) {
            return getDataType(code, detailedType);
        }
        return new TemporaryDataType(code, detailedType);
    }

    /**
     * Register a data type of any sort, including document types.
     * @param type The datatype to register
     * TODO Give unique ids to document types
     */
    public void register(DataType type) {
        type.register(this); // Recursively walk through all nested types and call registerSingleType for each one
    }

    /**
     * Register a single datatype. Re-registering an existing, but equal, datatype is ok.
     *
     * @param type The datatype to register
     */
    void registerSingleType(DataType type) {
        if (type instanceof TensorDataType) return; // built-in dynamic: Created on the fly
        if (dataTypes.containsKey(type.getId())) {
            DataType existingType = dataTypes.get(type.getId());
            if (((type instanceof TemporaryDataType) || (type instanceof TemporaryStructuredDataType))
                && !((existingType instanceof TemporaryDataType) || (existingType instanceof TemporaryStructuredDataType))) {
                //we're trying to register a temporary type over a permanent one, don't do that:
                return;
            } else if ((existingType == type || existingType.equals(type))
                    && !(existingType instanceof TemporaryDataType)
                    && !(type instanceof TemporaryDataType)
                    && !(existingType instanceof TemporaryStructuredDataType)
                    && !(type instanceof TemporaryStructuredDataType)) { // Shortcut to improve speed
                // Oki. Already registered.
                return;
            } else if (type instanceof DocumentType && dataTypes.get(type.getId()) instanceof DocumentType) {
                /*
                DocumentType newInstance = (DocumentType) type;
                DocumentType oldInstance = (DocumentType) dataTypes.get(type.getId());
                TODO fix tests
                */
                log.warning("Document type " + existingType + " is not equal to document type attempted registered " + type
                        + ", but have same name. OVERWRITING TYPE as many tests currently does this. "
                        + "Fix tests so we can throw exception here.");
                //throw new IllegalStateException("Datatype " + existingType + " is not equal to datatype attempted registered "
                //        + type + ", but already uses id " + type.getId());
            } else if ((existingType instanceof TemporaryDataType) || (existingType instanceof TemporaryStructuredDataType)) {
                //removing temporary type to be able to register correct type
                dataTypes.remove(existingType.getId());
            } else {
                throw new IllegalStateException("Datatype " + existingType + " is not equal to datatype attempted registered "
                                                + type + ", but already uses id " + type.getId());
            }
        }

        if (type instanceof DocumentType) {
            DocumentType docType = (DocumentType) type;
            if (docType.getInheritedTypes().size() == 0) {
                docType.inherit(documentTypes.get(new DataTypeName("document")));
            }
            documentTypes.put(docType.getDataTypeName(), docType);
        }
        dataTypes.put(type.getId(), type);
        type.setRegistered();
    }

    /**
     * Registers a document type. Typically called by someone
     * importing the document types from the common Vespa repository.
     *
     * @param docType The document type to register.
     * @return the previously registered type, or null if none was registered
     */
    public DocumentType registerDocumentType(DocumentType docType) {
        register(docType);
        return docType;
    }

    /**
     * Gets a registered document.
     *
     * @param name the document name of the type
     * @return returns the document type found,
     *         or null if there is no type with this name
     */
    public DocumentType getDocumentType(DataTypeName name) {
        return documentTypes.get(name);
    }

    /**
     * Returns a registered document type
     *
     * @param name    the type name of the document type
     * @return returns the document type having this name, or null if none
     */
    public DocumentType getDocumentType(String name) {
        return documentTypes.get(new DataTypeName(name));
    }

    final public Document createDocument(GrowableByteBuffer buf) {
        DocumentDeserializer data = DocumentDeserializerFactory.create6(this, buf);
        return new Document(data);
    }
    public Document createDocument(DocumentDeserializer data) {
        return new Document(data);
    }

    /**
     * Returns a read only view of the registered data types
     *
     * @return collection of types
     */
    public Collection<DataType> getDataTypes() {
        return Collections.unmodifiableCollection(dataTypes.values());
    }

    /**
     * A read only view of the registered document types
     * @return map of types
     */
    public Map<DataTypeName, DocumentType> getDocumentTypes() {
        return Collections.unmodifiableMap(documentTypes);
    }

    public Iterator<DocumentType> documentTypeIterator() {
        return documentTypes.values().iterator();
    }

    /**
     * Clears the DocumentTypeManager. After this operation,
     * only the default document type and data types are available.
     */
    public void clear() {
        documentTypes.clear();
        dataTypes.clear();
        registerDefaultDataTypes();
    }

    public AnnotationTypeRegistry getAnnotationTypeRegistry() {
        return annotationTypeRegistry;
    }

    void replaceTemporaryTypes() {
        for (DataType type : dataTypes.values()) {
            List<DataType> seenStructs = new LinkedList<>();
            replaceTemporaryTypes(type, seenStructs);
        }
    }

    private void replaceTemporaryTypes(DataType type, List<DataType> seenStructs) {
        if (type instanceof WeightedSetDataType) {
            replaceTemporaryTypesInWeightedSet((WeightedSetDataType) type, seenStructs);
        } else if (type instanceof MapDataType) {
            replaceTemporaryTypesInMap((MapDataType) type, seenStructs);
        } else if (type instanceof CollectionDataType) {
            replaceTemporaryTypesInCollection((CollectionDataType) type, seenStructs);
        } else if (type instanceof StructDataType) {
            replaceTemporaryTypesInStruct((StructDataType) type, seenStructs);
        } else if (type instanceof PrimitiveDataType) {
            //OK because these types are always present
        } else if (type instanceof AnnotationReferenceDataType) {
            //OK because this type is always present
        } else if (type instanceof DocumentType) {
            //OK because this type is always present
        } else if (type instanceof TensorDataType) {
            //OK because this type is always present
        } else if (type instanceof ReferenceDataType) {
            replaceTemporaryTypeInReference((ReferenceDataType) type);
        } else if (type instanceof TemporaryDataType) {
            throw new IllegalStateException("TemporaryDataType registered in DocumentTypeManager, BUG!!");
        } else {
            log.warning("Don't know how to replace temporary data types in " + type);
        }
    }

    @SuppressWarnings("deprecation")
    private void replaceTemporaryTypesInStruct(StructDataType structDataType, List<DataType> seenStructs) {
        seenStructs.add(structDataType);
        for (Field field : structDataType.getFieldsThisTypeOnly()) {
            DataType fieldType = field.getDataType();
            if (fieldType instanceof TemporaryDataType) {
                field.setDataType(getDataType(fieldType.getCode(), ((TemporaryDataType)fieldType).getDetailedType()));
            } else {
                if (!seenStructs.contains(fieldType)) {
                    replaceTemporaryTypes(fieldType, seenStructs);
                }
            }
        }
    }

    private void replaceTemporaryTypeInReference(ReferenceDataType referenceDataType) {
        if (referenceDataType.getTargetType() instanceof TemporaryStructuredDataType) {
            referenceDataType.setTargetType((DocumentType) getDataType(referenceDataType.getTargetType().getId()));
        }
        // TODO should we recursively invoke replaceTemporaryTypes for the target type? It should only ever be a doc type
    }

    private void replaceTemporaryTypesInCollection(CollectionDataType collectionDataType, List<DataType> seenStructs) {
        if (collectionDataType.getNestedType() instanceof TemporaryDataType) {
            collectionDataType.setNestedType(getDataType(collectionDataType.getNestedType().getCode(), ""));
        } else {
            replaceTemporaryTypes(collectionDataType.getNestedType(), seenStructs);
        }
    }

    private void replaceTemporaryTypesInMap(MapDataType mapDataType, List<DataType> seenStructs) {
        if (mapDataType.getValueType() instanceof TemporaryDataType) {
            mapDataType.setValueType(getDataType(mapDataType.getValueType().getCode(), ""));
        } else {
            replaceTemporaryTypes(mapDataType.getValueType(), seenStructs);
        }

        if (mapDataType.getKeyType() instanceof TemporaryDataType) {
            mapDataType.setKeyType(getDataType(mapDataType.getKeyType().getCode(), ""));
        } else {
            replaceTemporaryTypes(mapDataType.getKeyType(), seenStructs);
        }
    }

    private void replaceTemporaryTypesInWeightedSet(WeightedSetDataType weightedSetDataType, List<DataType> seenStructs) {
        if (weightedSetDataType.getNestedType() instanceof TemporaryDataType) {
            weightedSetDataType.setNestedType(getDataType(weightedSetDataType.getNestedType().getCode(), ""));
        } else {
            replaceTemporaryTypes(weightedSetDataType.getNestedType(), seenStructs);
        }
    }

    public void shutdown() {
        if (subscriber!=null) subscriber.close();
    }
}
