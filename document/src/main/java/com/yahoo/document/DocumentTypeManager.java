// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document;

import com.yahoo.component.annotation.Inject;
import com.yahoo.document.annotation.AnnotationType;
import com.yahoo.document.annotation.AnnotationTypeRegistry;
import com.yahoo.document.annotation.AnnotationTypes;
import com.yahoo.document.config.DocumentmanagerConfig;
import com.yahoo.document.serialization.DocumentDeserializer;
import com.yahoo.document.serialization.DocumentDeserializerFactory;
import com.yahoo.io.GrowableByteBuffer;
import com.yahoo.tensor.TensorType;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The DocumentTypeManager keeps track of the document types registered in
 * the Vespa common repository.
 *
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
    private boolean ignoreUndefinedFields = false;

    public DocumentTypeManager() {
        registerDefaultDataTypes();
    }

    @Inject
    public DocumentTypeManager(DocumentmanagerConfig config) {
        this();
        DocumentTypeManagerConfigurer.configureNewManager(config, this);
    }

    void internalAssign(DocumentTypeManager other) {
        dataTypes = other.dataTypes;
        documentTypes = other.documentTypes;
        annotationTypeRegistry = other.annotationTypeRegistry;
    }

    /** Only for unit tests */
    public static DocumentTypeManager fromFile(String fileName) {
        var manager = new DocumentTypeManager();
        var sub = DocumentTypeManagerConfigurer.configure(manager, "file:" + fileName);
        sub.close();
        return manager;
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

    /**
     * For internal use only, avoid whenever possible.
     * Use constants and factories in DataType instead.
     * For structs, use getStructType() in DocumentType.
     * For annotation payloads, use getDataType() in AnnotationType.
     */
    DataType getDataTypeInternal(String name) {
        if (name.startsWith("tensor(")) // built-in dynamic
            return new TensorDataType(TensorType.fromSpec(name));

        List<DataType> foundTypes = new ArrayList<>();
        for (DataType type : dataTypes.values()) {
            if (type.getName().equalsIgnoreCase(name)) {
                foundTypes.add(type);
            }
        }
        if (foundTypes.isEmpty()) {
            return null;
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

    /**
     * Returns true if we should ignore attempts to set a field not defined in the document type,
     * rather than (by default) throwing an exception.
     */
    public boolean getIgnoreUndefinedFields() { return ignoreUndefinedFields; }
    public void setIgnoreUndefinedFields(boolean ignoreUndefinedFields) { this.ignoreUndefinedFields = ignoreUndefinedFields; }

    /**
     * Return a data type instance
     *
     * @param code the code of the data type to return, which must be either built in or present in this manager
     */
    DataType getDataTypeByCode(int code) { return getDataTypeByCode(code, ""); }

    /**
     * Return a data type instance
     *
     * @param code the code of the data type to return, which must be either built in or present in this manager
     * @param detailedType detailed type information, or the empty string if none
     * @return the appropriate DataType instance
     */
    DataType getDataTypeByCode(int code, String detailedType) {
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
            if ((existingType == type) || existingType.equals(type)) {
                // Oki. Already registered.
                return;
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

    /**
     * Convenience method
     * @param name the name of a document type
     * @return returns true if a document type having this name is registered in this manager
     */
    public boolean hasDocumentType(String name) {
        return (getDocumentType(name) != null);
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
    void internalClear() {
        documentTypes.clear();
        dataTypes.clear();
        registerDefaultDataTypes();
    }

    public AnnotationTypeRegistry getAnnotationTypeRegistry() {
        return annotationTypeRegistry;
    }

}
