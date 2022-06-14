// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentmodel;

import com.yahoo.document.DataType;
import com.yahoo.document.Document;
import com.yahoo.document.Field;
import com.yahoo.document.StructDataType;
import com.yahoo.document.StructuredDataType;
import com.yahoo.document.annotation.AnnotationType;
import com.yahoo.document.annotation.AnnotationTypeRegistry;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.schema.FieldSets;
import com.yahoo.schema.Schema;
import com.yahoo.schema.document.FieldSet;
import com.yahoo.schema.processing.BuiltInFieldSets;

import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static java.util.Collections.emptySet;

/**
 * @author baldersheim
 */
public final class NewDocumentType extends StructuredDataType implements DataTypeCollection {

    private final Name name;
    private final DataTypeRepo dataTypes = new DataTypeRepo();
    private final Map<Integer, NewDocumentType> inherits = new LinkedHashMap<>();
    private final AnnotationTypeRegistry annotations = new AnnotationTypeRegistry();
    private final StructDataType contentStruct;
    private final Set<FieldSet> fieldSets = new LinkedHashSet<>();
    private final Set<Name> documentReferences;
    // Imported fields are virtual and therefore exist outside of the SD's document field definition
    // block itself. But for features like imported fields in a non-search context (e.g. GC selections)
    // it is necessary to know that certain identifiers refer to imported fields instead of being unknown
    // document fields. To achieve this, we track the names of imported fields as part of the document
    // config itself.
    private final Set<String> importedFieldNames;

    public NewDocumentType(Name name) {
        this(name, emptySet());
    }

    public NewDocumentType(Name name, Set<Name> documentReferences, Set<String> importedFieldNames) {
        this(
                name,
                new StructDataType(name.getName() + ".header"),
                new FieldSets(Optional.empty()),
                documentReferences,
                importedFieldNames);
    }

    public NewDocumentType(Name name, Set<Name> documentReferences) {
        this(name, documentReferences, emptySet());
    }

    public NewDocumentType(Name name,
                           StructDataType contentStruct,
                           FieldSets fs,
                           Set<Name> documentReferences,
                           Set<String> importedFieldNames) {
        super(name.getName());
        this.name = name;
        this.contentStruct = contentStruct;
        if (fs != null) {
            this.fieldSets.addAll(fs.userFieldSets().values());
            for (FieldSet f : fs.builtInFieldSets().values()) {
                if ((f.getName() != BuiltInFieldSets.INTERNAL_FIELDSET_NAME) &&
                    (f.getName() != BuiltInFieldSets.SEARCH_FIELDSET_NAME)) {
                    fieldSets.add(f);
                }
            }
        }
        this.documentReferences = documentReferences;
        this.importedFieldNames = importedFieldNames;
    }

    public Name getFullName() {
        return name;
    }

    public DataType getContentStruct() { return contentStruct; }
    public Collection<NewDocumentType> getInherited() { return inherits.values(); }
    public NewDocumentType getInherited(Name inherited) { return inherits.get(inherited.getId()); }

    @Override
    public Class<Document> getValueClass() {
        return Document.class;
    }

    @Override
    public boolean isValueCompatible(FieldValue value) {
        if (!(value instanceof Document)) {
            return false;
        }
        /*
         Temporary disabled  due to clash with document and covariant return type
         Document doc = (Document) value;
         if (((NewDocumentType) doc.getDataType()).inherits(this)) {
         //the value is of this type; or the supertype of the value is of this type, etc....
         return true;
         }
         */
        return false;
    }

    private void verifyInheritance(NewDocumentType inherited) {
        for (Field f : getFields()) {
            Field inhF = inherited.getField(f.getName());
            if (inhF != null && !inhF.equals(f)) {
                 throw new IllegalArgumentException("Inherited document '" + inherited + "' already contains field '" +
                                                    inhF.getName() + "'. Can not override with '" + f.getName() + "'.");
            }
        }
        for (Field f : inherited.getAllFields()) {
            for (NewDocumentType side : inherits.values()) {
                Field sideF = side.getField(f.getName());
                if (sideF != null && !sideF.equals(f)) {
                    throw new IllegalArgumentException("Inherited document '" + side + "' already contains field '" +
                                                       sideF.getName() + "'. Document '" + inherited +
                                                       "' also defines field '" + f.getName() +
                                                       "'.Multiple inheritance must be disjunctive.");
                }
            }
        }
    }

    public void inherit(NewDocumentType inherited) {
        if ( ! inherits.containsKey(inherited.getId())) {
            verifyInheritance(inherited);
            inherits.put(inherited.getId(), inherited);
        }
    }

    public boolean inherits(NewDocumentType superType) {
        if (getId() == superType.getId()) return true;
        for (NewDocumentType type : inherits.values()) {
            if (type.inherits(superType)) return true;
        }
        return false;
    }

    @Override
    public Field getField(String name) {
        Field field = contentStruct.getField(name);
        if (field == null) {
            for (NewDocumentType inheritedType : inherits.values()) {
                field = inheritedType.getField(name);
                if (field != null) {
                    return field;
                }
            }
        }
        return field;
    }

    public boolean containsField(String fieldName) {
        return getField(fieldName) != null;
    }

    @Override
    public Field getField(int id) {
        Field field = contentStruct.getField(id);
        if (field == null) {
            for (NewDocumentType inheritedType : inherits.values()) {
                field = inheritedType.getField(id);
                if (field != null) {
                    return field;
                }
            }
        }
        return field;
    }

    public Collection<Field> getAllFields() {
        Collection<Field> collection = new LinkedList<>();

        for (NewDocumentType type : inherits.values()) {
            collection.addAll(type.getAllFields());
        }

        collection.addAll(contentStruct.getFields());
        return Collections.unmodifiableCollection(collection);
    }

    public Collection<Field> getFields() {
        return contentStruct.getFields();
    }

    @Override
    public Document createFieldValue() {
        throw new RuntimeException("Cannot create an instance of " + this);
    }

    @Override
    public Collection<DataType> getTypes() {
        return dataTypes.getTypes();
    }

    public DataTypeCollection getAllTypes() {
        DataTypeRepo repo = new DataTypeRepo();
        Set<Name> seen = new HashSet<>();
        Deque<NewDocumentType> stack = new LinkedList<>();
        stack.push(this);
        while (!stack.isEmpty()) {
            NewDocumentType docType = stack.pop();
            if (seen.contains(docType.name)) {
                continue; // base type
            }
            seen.add(docType.name);
            for (DataType dataType : docType.getTypes()) {
                if (repo.getDataType(dataType.getId()) == null) {
                    repo.add(dataType);
                }
            }
            stack.addAll(docType.inherits.values());
        }
        return repo;
    }

    public Collection<AnnotationType> getAnnotations() { return annotations.getTypes().values(); }
    public Collection<AnnotationType> getAllAnnotations() {
        Collection<AnnotationType> collection = new LinkedList<>();

        for (NewDocumentType type : inherits.values()) {
            collection.addAll(type.getAllAnnotations());
        }
        collection.addAll(getAnnotations());

        return Collections.unmodifiableCollection(collection);
    }

    public DataType getDataType(String name) {
        return dataTypes.getDataType(name);
    }
    public DataType getDataType(int id) {
        return dataTypes.getDataType(id);
    }
    public DataType getDataTypeRecursive(String name) {
        DataType a = dataTypes.getDataType(name);
        if (a != null) {
            return a;
        } else {
            for (NewDocumentType dt : getInherited()) {
                a = dt.getDataTypeRecursive(name);
                if (a != null) {
                    return a;
                }
            }
        }
        return null;
    }

    public DataType getDataTypeRecursive(int id) {
        DataType a = dataTypes.getDataType(id);
        if (a != null) {
            return a;
        } else {
            for (NewDocumentType dt : getInherited()) {
                a = dt.getDataTypeRecursive(id);
                if (a != null) {
                    return a;
                }
            }
        }
        return null;
    }

    public AnnotationType getAnnotationType(String name) {
        AnnotationType a = annotations.getType(name);
        if (a != null) {
            return a;
        } else {
            for (NewDocumentType dt : getInherited()) {
                a = dt.getAnnotationType(name);
                if (a != null) {
                    return a;
                }
            }
        }
        return null;
    }

    public NewDocumentType add(AnnotationType type) {
        annotations.register(type);
        return this;
    }
    public NewDocumentType add(DataType type) {
        dataTypes.add(type);
        return this;
    }
    public NewDocumentType replace(DataType type) {
        dataTypes.replace(type);
        return this;
    }

    /** The field sets defined for this type and its {@link Schema} */
    public Set<FieldSet> getFieldSets() {
        return Collections.unmodifiableSet(fieldSets);
    }

    public Set<Name> getDocumentReferences() {
        return documentReferences;
    }

    public Set<String> getImportedFieldNames() {
        return importedFieldNames;
    }

    public static final class Name {

        private final String name;
        private final int id;

        public Name(String name) {
            this(name.hashCode(), name);
        }

        public Name(int id, String name) {
            this.id = id;
            this.name = name;
        }

        @Override
        public String toString() { return name; }

        public String getName() { return name; }

        public int getId() { return id; }

        @Override
        public int hashCode() { return name.hashCode(); }

        @Override
        public boolean equals(Object other) {
            if ( ! (other instanceof Name)) return false;
            return name.equals(((Name)other).getName());
        }

    }

    private NewDocumentReferenceDataType refToThis = null;

    public NewDocumentReferenceDataType getReferenceDataType() {
        if (refToThis == null) {
            refToThis = new NewDocumentReferenceDataType(this);
        }
        return refToThis;
    }

}
