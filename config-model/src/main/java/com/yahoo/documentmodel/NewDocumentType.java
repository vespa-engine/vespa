// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentmodel;

import com.yahoo.document.DataType;
import com.yahoo.document.Document;
import com.yahoo.document.DocumentId;
import com.yahoo.document.Field;
import com.yahoo.document.StructDataType;
import com.yahoo.document.StructuredDataType;
import com.yahoo.document.annotation.AnnotationType;
import com.yahoo.document.annotation.AnnotationTypeRegistry;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.searchdefinition.FieldSets;
import com.yahoo.searchdefinition.Search;
import com.yahoo.searchdefinition.document.FieldSet;
import com.yahoo.searchdefinition.processing.BuiltInFieldSets;

import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;

import static java.util.Collections.emptySet;

/**
 * @author baldersheim
 */
public final class NewDocumentType extends StructuredDataType implements DataTypeCollection {


    public static final class Name {

        // TODO: privatize
        final String name;
        final int id;

        public Name(String name) {
            this(name.hashCode(),name);
        }

        public Name(int id,String name) {
            this.id = id;
            this.name = name;
        }

        public String toString() { return name; }

        public final String getName() { return name; }

        public final int getId() { return id; }

        public int hashCode() { return name.hashCode(); }

        public boolean equals(Object other) {
            if ( ! (other instanceof Name)) return false;
            return name.equals(((Name)other).getName());
        }
    }

    private final Name name;
    private final DataTypeRepo dataTypes = new DataTypeRepo();
    private final Map<Integer, NewDocumentType> inherits = new LinkedHashMap<>();
    private final AnnotationTypeRegistry annotations = new AnnotationTypeRegistry();
    private final StructDataType header;
    private final StructDataType body;
    private final Set<FieldSet> fieldSets = new LinkedHashSet<>();
    private final Set<Name> documentReferences;

    public NewDocumentType(Name name) {
        this(name, emptySet());
    }

    public NewDocumentType(Name name, Set<Name> documentReferences) {
        this(
                name,
                new StructDataType(name.getName() + ".header"),
                new StructDataType(name.getName() + ".body"),
                new FieldSets(),
                documentReferences);
    }

    public NewDocumentType(Name name,
                           StructDataType header,
                           StructDataType body,
                           FieldSets fs,
                           Set<Name> documentReferences) {
        super(name.getName());
        this.name = name;
        this.header = header;
        this.body = body;
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
    }

    public Name getFullName() {
        return name;
    }

    public DataType getHeader() { return header; }
    public DataType getBody() { return body; }
    public Collection<NewDocumentType> getInherited() { return inherits.values(); }
    public NewDocumentType getInherited(Name inherited) { return inherits.get(inherited.getId()); }
    public NewDocumentType removeInherited(Name inherited) { return inherits.remove(inherited.getId()); }

    /**
     * Data type of the header fields of this and all inherited document types
     * @return merged {@link StructDataType}
     */
    public StructDataType allHeader() {
        StructDataType ret = new StructDataType(header.getName());
        for (Field f : header.getFields()) {
            ret.addField(f);
        }
        for (NewDocumentType inherited : getInherited()) {
            for (Field f : ((StructDataType) inherited.getHeader()).getFields()) {
                ret.addField(f);
            }
        }
        return ret;
    }

    /**
     * Data type of the body fields of this and all inherited document types
     * @return merged {@link StructDataType}
     */
    public StructDataType allBody() {
        StructDataType ret = new StructDataType(body.getName());
        for (Field f : body.getFields()) {
            ret.addField(f);
        }
        for (NewDocumentType inherited : getInherited()) {
            for (Field f : ((StructDataType) inherited.getBody()).getFields()) {
                ret.addField(f);
            }
        }
        return ret;
    }

    @Override
    public Class getValueClass() {
        return Document.class;
    }

    @Override
    public boolean isValueCompatible(FieldValue value) {
        if (!(value instanceof Document)) {
            return false;
        }
        /** Temporary disabled  due to clash with document and covariant return type
         Document doc = (Document) value;
         if (((NewDocumentType) doc.getDataType()).inherits(this)) {
         //the value is of this type; or the supertype of the value is of this type, etc....
         return true;
         }
         */
        return false;
    }

    private boolean verifyInheritance(NewDocumentType inherited) {
        for (Field f : getFields()) {
            Field inhF = inherited.getField(f.getName());
            if (inhF != null && !inhF.equals(f)) {
                 throw new IllegalArgumentException("Inherited document '" + inherited.toString() + "' already contains field '" +
                         inhF.getName() + "'. Can not override with '" + f.getName() + "'.");
            }
        }
        for (Field f : inherited.getAllFields()) {
            for (NewDocumentType side : inherits.values()) {
                Field sideF = side.getField(f.getName());
                if (sideF != null && !sideF.equals(f)) {
                    throw new IllegalArgumentException("Inherited document '" + side.toString() + "' already contains field '" +
                            sideF.getName() + "'. Document '" + inherited.toString() + "' also defines field '" + f.getName() +
                            "'.Multiple inheritance must be disjunctive.");
                }
            }
        }
        return true;
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
        Field field = header.getField(name);
        if (field == null) {
            field = body.getField(name);
        }
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
        Field field = header.getField(id);
        if (field == null) {
            field = body.getField(id);
        }
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

        collection.addAll(header.getFields());
        collection.addAll(body.getFields());
        return Collections.unmodifiableCollection(collection);
    }

    public Collection<Field> getFields() {
        Collection<Field> collection = new LinkedList<>();
        collection.addAll(header.getFields());
        collection.addAll(body.getFields());
        return Collections.unmodifiableCollection(collection);
    }

    @Override
    public Document createFieldValue() {
        return new Document(null, (DocumentId)null);
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
    public AnnotationType getAnnotationType(int id) {
        AnnotationType a = annotations.getType(id);
        if (a != null) {
            return a;
        } else {
            for (NewDocumentType dt : getInherited()) {
                a = dt.getAnnotationType(id);
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

    /**
     * The field sets defined for this type and its {@link Search}
     * @return fieldsets
     */
    public Set<FieldSet> getFieldSets() {
        return Collections.unmodifiableSet(fieldSets);
    }

    public Set<Name> getDocumentReferences() {
        return documentReferences;
    }

}
