// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.document;

import com.yahoo.document.DataType;
import com.yahoo.document.DataTypeName;
import com.yahoo.document.DocumentType;
import com.yahoo.document.Field;
import com.yahoo.document.PositionDataType;
import com.yahoo.document.StructDataType;
import com.yahoo.document.annotation.AnnotationType;
import com.yahoo.document.annotation.AnnotationTypeRegistry;
import com.yahoo.documentmodel.NewDocumentType;
import com.yahoo.documentmodel.VespaDocumentType;
import com.yahoo.schema.DocumentReferences;
import com.yahoo.schema.FieldSets;
import com.yahoo.schema.Schema;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A document definition is a list of fields. Documents may inherit other documents,
 * implicitly acquiring their fields as it's own. If a document is not set to inherit
 * any document, it will always inherit the document "document.0".
 *
 * @author Thomas Gundersen
 * @author bratseth
 */
public class SDDocumentType implements Cloneable {

    public static final SDDocumentType VESPA_DOCUMENT;
    private final Map<DataTypeName, SDDocumentType> inheritedTypes = new LinkedHashMap<>();
    private final Map<NewDocumentType.Name, SDDocumentType> ownedTypes = new LinkedHashMap<>();
    private final AnnotationTypeRegistry annotationTypes = new AnnotationTypeRegistry();
    private DocumentType docType;    
    private DataType structType;
    // The field sets here are set from the processing step in SD,
    // to ensure that the full Search and this SDDocumentType is built first.
    private FieldSets fieldSets;
    // Document references
    private DocumentReferences documentReferences;
    private TemporaryImportedFields temporaryImportedFields;

    static {
        VESPA_DOCUMENT = new SDDocumentType(VespaDocumentType.INSTANCE.getFullName().getName());
        VESPA_DOCUMENT.addType(createSDDocumentType(PositionDataType.INSTANCE));
    }

    public SDDocumentType clone() throws CloneNotSupportedException {
        SDDocumentType type = (SDDocumentType) super.clone();
        type.docType = docType.clone();
        type.inheritedTypes.putAll(inheritedTypes);
        type.structType = structType;
        // TODO this isn't complete; should it be..?!
        return type;
    }

    /**
     * For adding structs defined in document scope
     *
     * @param dt the struct to add
     * @return self, for chaining
     */
    public SDDocumentType addType(SDDocumentType dt) {
        NewDocumentType.Name name = new NewDocumentType.Name(dt.getName());
        if (getType(name) != null)
            throw new IllegalArgumentException("Data type '" + name + "' has already been used.");
        if (name.getName().equals(docType.getName()))
            throw new IllegalArgumentException("Data type '" + name + "' can not have same name as its defining document.");
        ownedTypes.put(name, dt);
        return this;
    }
    public final SDDocumentType getOwnedType(String name) {
         return getOwnedType(new NewDocumentType.Name(name));
    }

    public SDDocumentType getOwnedType(NewDocumentType.Name name) {
        return ownedTypes.get(name);
    }

    public final SDDocumentType getType(String name) {
        return getType(new NewDocumentType.Name(name));
    }

    public SDDocumentType getType(NewDocumentType.Name name) {
        SDDocumentType type = ownedTypes.get(name);
        if (type == null) {
            for (SDDocumentType inherited : inheritedTypes.values()) {
                type = inherited.getType(name);
                if (type != null) {
                    return type;
                }
            }
        }
        return type;
    }

    public void addAnnotation(AnnotationType annotation) {
        annotationTypes.register(annotation);
    }

    /** Returns all owned datatypes. */
    public Collection<SDDocumentType> getTypes() { return ownedTypes.values(); }

    // TODO: Include inherited
    public Map<String, AnnotationType> getAnnotations() { return annotationTypes.getTypes(); }
    public AnnotationType findAnnotation(String name) { return annotationTypes.getType(name); }

    public Map<NewDocumentType.Name, SDDocumentType> allTypes() {
        Map<NewDocumentType.Name, SDDocumentType> map = new LinkedHashMap<>();
        for (SDDocumentType inherited : inheritedTypes.values())
            map.putAll(inherited.allTypes());
        map.putAll(ownedTypes);
        return map;
    }

    /**
     * Creates a new document type.
     * The document type id will be generated as a hash from the document type name.
     *
     * @param name The name of the new document type
    */
    public SDDocumentType(String name) {
        this(name,null);
    }

    public SDDocumentType(DataTypeName name) {
        this(name.getName());
    }

    /**
     * Creates a new document type.
     * The document type id will be generated as a hash from the document type name.
     *
     * @param name the name of the new document type
     * @param schema check for type ID collisions in this search definition
     */
    public SDDocumentType(String name, Schema schema) {
        docType = new DocumentType(name);
        validateId(schema);
        inherit(VESPA_DOCUMENT);
    }

    public boolean isStruct() { return getStruct() != null; }
    public DataType getStruct() { return structType; }
    public SDDocumentType setStruct(DataType structType) {
        if (structType != null) {
            this.structType = structType;
            inheritedTypes.remove(VESPA_DOCUMENT.getDocumentName());
        } else {
            if (docType.contentStruct() != null) {
                this.structType = docType.contentStruct();
                inheritedTypes.clear();
            } else {
                throw new IllegalArgumentException("You can not set a null struct");
            }
        }
        return this;
    }

    public String getName() { return docType.getName(); }
    public DataTypeName getDocumentName() { return docType.getDataTypeName(); }
    public DocumentType getDocumentType() { return docType; }

    public void inherit(SDDocumentType type) {
        if (type == null) return;
        if (type.getName().equals(this.getName()))
            throw new IllegalArgumentException("Document type '" + getName() + "' cannot inherit itself");
        if (! inheritedTypes.containsKey(type.getDocumentName())) {
            inheritedTypes.put(type.getDocumentName(), type);
        }
    }

    public Collection<SDDocumentType>  getInheritedTypes() { return inheritedTypes.values(); }

    public Map<DataTypeName, SDDocumentType>  inheritedTypes() { return inheritedTypes; }

    protected void validateId(Schema schema) {
        if (schema == null) return;
        if (schema.getDocument(getName()) == null) return;
        SDDocumentType doc = schema.getDocument();
        throw new IllegalArgumentException("Failed creating document type '" + getName() + "', " +
                                           "document type '" + doc.getName() + "' already uses ID '" + doc.getName() + "'");
    }

    public void setFieldId(SDField field, int id) {
        field.setId(id, docType);
    }

    /** Override getField, as it may need to ask inherited types that isn't registered in document type. */
    public Field getField(String name) {
        if (name.contains(".")) {
            String superFieldName = name.substring(0,name.indexOf("."));
            String subFieldName = name.substring(name.indexOf(".")+1);
            Field f = docType.getField(superFieldName);
            if (f != null) {
                if (f instanceof SDField superField) {
                    return superField.getStructField(subFieldName);
                } else {
                    throw new IllegalArgumentException("Field " + f.getName() + " is not an SDField");
                }
            }
        }
        Field f = docType.getField(name);
        if (f == null) {
            for(SDDocumentType parent : inheritedTypes.values()) {
                f = parent.getField(name);
                if (f != null) return f;
            }
        }
        return f;
    }

    public void addField(Field field) {
    	verifyInheritance(field);
        for (Iterator<Field> i = docType.fieldIteratorThisTypeOnly(); i.hasNext(); ) {
            if (field.getName().equalsIgnoreCase((i.next()).getName())) {
                throw new IllegalArgumentException("Duplicate (case insensitively) " + field + " in " + this);
            }
        }
        docType.addField(field);
    }

    /** Parse-time inheritance check. */
    private void verifyInheritance(Field field) {
    	for (SDDocumentType parent : inheritedTypes.values()) {
            for (Field pField : parent.fieldSet()) {
            	if (pField.getName().equals(field.getName())) {
            		if (!pField.getDataType().equals(field.getDataType())) {
            			throw new IllegalArgumentException("For " + this + ", field '" + field.getName() +
                                                           "': Datatype can not be different from that of same field " +
                                                           "in the supertype '" + parent.getName() + "'");
            		}
            	}
            }
    	}
    }

    public SDField addField(String string, DataType dataType) {
        SDField field = new SDField(this, string, dataType);
        addField(field);
        return field;
    }

    public Field addField(String fName, DataType dataType, int code) {
        SDField field = new SDField(this, fName, code, dataType);
        addField(field);
        return field;
    }

    private Map<String, Field> fieldsInherited() {
    	Map<String, Field> map = new LinkedHashMap<>();
        for (SDDocumentType parent : inheritedTypes.values()) {
            for (Field field : parent.fieldSet()) {
                map.put(field.getName(), field);
            }
        }
        return map;
    }

    public Set<Field> fieldSet() {
        Map<String, Field> map = fieldsInherited();
        Iterator<Field> it = docType.fieldIteratorThisTypeOnly();
        while (it.hasNext()) {
            Field field = it.next();
            map.put(field.getName(), field);
        }
        return new LinkedHashSet<>(map.values());
    }

    /** Returns the number of fields in this only, not including inherited fields */
    // TODO: Remove
    public int getFieldCount() {
        return docType.getFieldCount();
    }

    @Override
    public String toString() {
        return "document type '" + docType.getName() + "'";
    }

    private static SDDocumentType createSDDocumentType(StructDataType structType) {
        SDDocumentType docType = new SDDocumentType(structType.getName());
        for (Field field : structType.getFields()) {
            docType.addField(new SDField(docType, field.getName(), field.getDataType()));
        }
        docType.setStruct(structType);
        return docType;
    }
    
    /** The field sets defined for this type and its {@link Schema} */
    public FieldSets getFieldSets() {
        return fieldSets;
    }

    /** Sets the field sets for this */
    public void setFieldSets(FieldSets fieldSets) {
        this.fieldSets = fieldSets;
    }

    public Optional<DocumentReferences> getDocumentReferences() {
        return Optional.ofNullable(documentReferences);
    }

    public void setDocumentReferences(DocumentReferences documentReferences) {
        this.documentReferences = documentReferences;
    }

    public TemporaryImportedFields getTemporaryImportedFields() {
        return temporaryImportedFields;
    }

    public void setTemporaryImportedFields(TemporaryImportedFields temporaryImportedFields) {
        this.temporaryImportedFields = temporaryImportedFields;
    }

}
