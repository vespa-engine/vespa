// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.fieldset.AllFields;
import com.yahoo.document.serialization.DocumentWriter;
import com.yahoo.vespa.objects.Ids;
import com.yahoo.vespa.objects.ObjectVisitor;
import com.yahoo.vespa.objects.Serializer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;


/**
 * <p>A document definition is a list of fields. Documents may inherit other documents,
 * implicitly acquiring their fields as it's own. If a document is not set to inherit
 * any document, it will always inherit the document "document.0".</p>
 *
 * @author Thomas Gundersen
 * @author bratseth
 */
public class DocumentType extends StructuredDataType {

    public static final String DOCUMENT = "[document]";
    public static final int classId = registerClass(Ids.document + 58, DocumentType.class);
    private StructDataType contentStructType;
    private List<DocumentType> inherits = new ArrayList<>(1);
    private Map<String, Set<Field>> fieldSets = new HashMap<>();
    private final Set<String> importedFieldNames;
    private Map<String, StructDataType> declaredStructTypes = new HashMap<>();

    /**
     * Creates a new document type and registers it with the document type manager.
     * This will be created as version 0 of this document type.
     * Implicitly registers this with the document type manager.
     * The document type id will be generated as a hash from the document type name.
     *
     * @param name The name of the new document type
     */
    public DocumentType(String name) {
        this(name, createContentStructType(name));
    }

    /**
     * Creates a new document type and registers it with the document type manager.
     * Implicitly registers this with the document type manager.
     * The document type id will be generated as a hash from the document type name.
     *
     * @param name       The name of the new document type
     * @param contentStructType The type of the content struct
     */
    public DocumentType(String name, StructDataType contentStructType) {
        this(name, contentStructType, Collections.emptySet());
    }

    public DocumentType(String name, StructDataType contentStructType, Set<String> importedFieldNames) {
        super(name);
        this.contentStructType = contentStructType;
        this.importedFieldNames = Collections.unmodifiableSet(importedFieldNames);
    }

    public DocumentType(String name, Set<String> importedFieldNames) {
        this(name, createContentStructType(name), importedFieldNames);
    }

    private static StructDataType createContentStructType(String name) {
        return new StructDataType(name + ".header");
    }

    @Override
    public DocumentType clone() {
        DocumentType type = (DocumentType) super.clone();
        type.contentStructType = contentStructType.clone();
        type.inherits = new ArrayList<>(inherits.size());
        type.inherits.addAll(inherits);
        return type;
    }

    @Override
    public Document createFieldValue() {
        return new Document(this, (DocumentId) null);
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
        Document doc = (Document) value;
        if (doc.getDataType().inherits(this)) {
            //the value is of this type; or the supertype of the value is of this type, etc....
            return true;
        }
        return false;
    }

    /**
     * Provides the Struct describing the fields in the document.
     *
     * @return a struct describing the document fields.
     */
    public StructDataType contentStruct() {
        return contentStructType;
    }

    /**
     * Get a struct declared in this document (or any inherited
     * document). Returns null if no such struct was found.
     * If multiple possible structs are found in inherited
     * documents, throws exception.
     *
     * @param name the name of the struct
     * @return reference to a struct data type, or null
     **/
    public StructDataType getStructType(String name) {
        var mine = declaredStructTypes.get(name);
        if (mine != null) {
            return mine;
        }
        for (DocumentType inheritedType : inherits) {
            var fromParent = inheritedType.getStructType(name);
            if (fromParent == null) {
                continue;
            } else if (mine == null) {
                mine = fromParent;
            } else if (mine != fromParent) {
                throw new IllegalArgumentException("Found multiple conflicting struct types for "+name);
            }
        }
        return mine;
    }

    /**
     * Get a struct declared in this document only.
     * Returns null if no such struct was found.
     *
     * @param name the name of the struct
     * @return reference to a struct data type, or null
     **/
    public StructDataType getDeclaredStructType(String name) {
        return declaredStructTypes.get(name);
    }

    /** only used during configuration */
    void addDeclaredStructType(String name, StructDataType struct) {
        var old = declaredStructTypes.put(name, struct);
        if (old != null) {
            throw new IllegalArgumentException("Already had declared struct for "+name);
        }
    }

    @Override
    protected void register(DocumentTypeManager manager, List<DataType> seenTypes) {
        seenTypes.add(this);
        for (DocumentType type : getInheritedTypes()) {
            if (!seenTypes.contains(type)) {
                type.register(manager, seenTypes);
            }
        }
        // Get parent fields into fields specified in this type
        StructDataType header = contentStructType.clone();

        header.clearFields();

        for (Field field : getAllUniqueFields()) {
            header.addField(field);
        }
        contentStructType.assign(header);

        if (!seenTypes.contains(contentStructType)) {
            contentStructType.register(manager, seenTypes);
        }
        manager.registerSingleType(this);
    }

    /**
     * Check if this document type has the given name,
     * or inherits from a type with that name.
     */
    public boolean isA(String docTypeName) {
        if (getName().equalsIgnoreCase(docTypeName)) {
            return true;
        }
        for (DocumentType parent : inherits) {
            if (parent.isA(docTypeName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Adds an field that can be used with this document type.
     *
     * @param field the field to add
     */
    public void addField(Field field) {
        if (isRegistered()) {
            throw new IllegalStateException("You cannot add fields to a document type that is already registered.");
        }
        contentStructType.addField(field);
    }

    // Do not use, public only for testing
    public void addFieldSets(Map<String, Collection<String>> fieldSets) {
        for (Map.Entry<String, Collection<String>> entry : fieldSets.entrySet()) {

            Set<Field> fields = new LinkedHashSet<>(entry.getValue().size());
            for (DocumentType parent : inherits) {
                Set<Field> parentFieldSet = parent.fieldSet(entry.getKey());
                if (parentFieldSet != null) {
                    fields.addAll(parentFieldSet);
                }
            }
            for (Field orderedField : getAllUniqueFields()) {
                if (entry.getValue().contains(orderedField.getName())) {
                    fields.add(orderedField);
                }
            }

            this.fieldSets.put(entry.getKey(), ImmutableSet.copyOf(fields));
        }
        if ( ! this.fieldSets.containsKey(AllFields.NAME)) {
            this.fieldSets.put(AllFields.NAME, getAllUniqueFields());
        }
    }

    /**
     * Adds a new field to this document type and returns the new field object
     *
     * @param name The name of the field to add
     * @param type The datatype of the field to add
     * @return The field created
     *         TODO Fix searchdefinition so that exception can be thrown if filed is already registerd.
     */
    public Field addField(String name, DataType type) {
        if (isRegistered()) {
            throw new IllegalStateException("You cannot add fields to a document type that is already registered.");
        }
        Field field = new Field(name, type);
        contentStructType.addField(field);
        return field;
    }

    /**
     * Adds a document to the inherited document types of this.
     * If this type is already directly inherited, nothing is done
     *
     * @param type An already DocumentType object.
     */
    public void inherit(DocumentType type) {
        //TODO: There is also a check like the following in SDDocumentType addField(), try to move that to this class' addField() to get it proper,
        // as this method is called only when the doc types are exported.
        verifyTypeConsistency(type);
        if (isRegistered()) {
            throw new IllegalStateException("You cannot add inheritance to a document type that is already registered.");
        }
        if (type == null) {
            throw new IllegalArgumentException("The document type cannot be null in inherit()");
        }

        // If it inherits the exact same type
        if (inherits.contains(type)) return;

        // If we inherit a type, don't inherit the supertype
        if (inherits.size() == 1 && inherits.get(0).getDataTypeName().equals(new DataTypeName("document"))) {
            inherits.clear();
        }

        inherits.add(type);
        for (var field : type.getAllUniqueFields()) {
            if (! contentStructType.hasField(field)) {
                contentStructType.addField(field);
            }
        }
    }

    /**
     * Fail if the subtype changes the type of any equally named field.
     *
     * @param superType The supertype to verify against
     *                  TODO Add strict type checking no duplicate fields are allowed
     */
    private void verifyTypeConsistency(DocumentType superType) {
        for (Field f : getAllUniqueFields()) {
            Field supField = superType.getField(f.getName());
            if (supField != null) {
                if (!f.getDataType().equals(supField.getDataType())) {
                    throw new IllegalArgumentException("Inheritance type mismatch: field \"" + f.getName() +
                                                       "\" in datatype \"" + getName() + "\"" +
                                                       " must have same datatype as in parent document type \"" + superType.getName() + "\"");
                }
            }
        }
    }

    /**
     * Returns the DocumentNames which are directly inherited by this
     * as a read-only collection.
     * If this document type does not explicitly inherit anything, the list will
     * contain the root type 'Document'
     *
     * @return a read-only list iterator containing the name Strings of the directly
     *         inherited document types of this
     */
    public Collection<DocumentType> getInheritedTypes() {
        return Collections.unmodifiableCollection(inherits);
    }

    public ListIterator<DataTypeName> inheritedIterator() {
        List<DataTypeName> names = new ArrayList<>(inherits.size());
        for (DocumentType type : inherits) {
            names.add(type.getDataTypeName());
        }
        return ImmutableList.copyOf(names).listIterator();
    }

    /**
     * Return whether this document type inherits the given document type.
     *
     * @param superType the documenttype to check if it inherits
     * @return true if it inherits the superType, false if not
     */
    public boolean inherits(DocumentType superType) {
        if (equals(superType)) return true;
        for (DocumentType type : inherits) {
            if (type.inherits(superType)) return true;
        }
        return false;
    }

    /**
     * Gets the field matching a given name.
     *
     * @param name the name of a field
     * @return returns the matching field, or null if not found
     */
    public Field getField(String name) {
        Field field = contentStructType.getField(name);
        if (field == null && !isRegistered()) {
            for (DocumentType inheritedType : inherits) {
                field = inheritedType.getField(name);
                if (field != null) break;
            }
        }
        return field;
    }

    @Override
    public Field getField(int id) {
        Field field = contentStructType.getField(id);
        if (field == null && !isRegistered()) {
            for (DocumentType inheritedType : inherits) {
                field = inheritedType.getField(id);
                if (field != null) break;
            }
        }
        return field;
    }

    /**
     * Returns whether this type defines the given field name
     *
     * @param name The name of the field to check if it has
     * @return True if there is a field with the given name.
     */
    public boolean hasField(String name) {
        return getField(name) != null;
    }

    public int getFieldCount() {
        return contentStructType.getFieldCount();
    }

    public Set<String> getImportedFieldNames() {
        return importedFieldNames;
    }

    public boolean hasImportedField(String fieldName) {
        return importedFieldNames.contains(fieldName);
    }

    /**
     * Removes an field from the DocumentType.
     *
     * @param name The name of the field.
     * @return The field that was removed or null if it did not exist.
     */
    public Field removeField(String name) {
        if (isRegistered()) {
            throw new IllegalStateException("You cannot remove fields from a document type that is already registered.");
        }
        Field field = contentStructType.removeField(name);
        if (field == null) {
            for (DocumentType inheritedType : inherits) {
                field = inheritedType.removeField(name);
                if (field != null) break;
            }
        }
        return field;
    }

    /**
     * All fields defined in the document and its parents
     * This is for internal use
     * Use {@link #fieldSet()} instead or {@link #fieldSetAll()} if you really want all fields
     * @return All fields defined in the document and its parents
     */
    @Override
    public Collection<Field> getFields() {
        Collection<Field> collection = new LinkedList<>();

        for (DocumentType type : inherits) {
            collection.addAll(type.getFields());
        }

        collection.addAll(contentStructType.getFields());
        return ImmutableList.copyOf(collection);
    }

    private Set<Field> getAllUniqueFields() {
        Map<String, Field> map = new LinkedHashMap<>();
        for (Field field : getFields()) { // Uniqify on field name
            map.put(field.getName(), field);
        }
        return ImmutableSet.copyOf(map.values());
    }

    /**
     * <p>Returns an ordered set snapshot of all fields of this documenttype,
     * <i>except the fields of Document</i>.
     * Only the overridden version will be returned for overridden fields.</p>
     *
     * <p>The fields of a document type has a well-defined order which is
     * exhibited in this set:
     * - Fields come in the order defined in the document type definition.
     * - The fields defined in inherited types come before those in
     * the document type itself.
     * - When a field in an inherited type is overridden, the value is overridden,
     * but not the ordering.
     * </p>
     *
     * @return an unmodifiable snapshot of the fields in this type
     */
    public Set<Field> fieldSet() {
        return fieldSet(DOCUMENT);
    }

    /**
     * This is identical to {@link #fieldSet()} fieldSet}, but in addition extra hidden synthetic fields are returned.
     * @return an unmodifiable snapshot of the all fields in this type
     */
    public Set<Field> fieldSetAll() {
        return fieldSet(AllFields.NAME);
    }

    public Set<Field> fieldSet(String name) {
        return fieldSets.get(name);
    }

    /**
     * Returns an iterator over all fields in this documenttype
     *
     * @return An iterator for iterating the fields in this documenttype.
     */
    public Iterator<Field> fieldIteratorThisTypeOnly() {
        return contentStructType.getFields().iterator();
    }

    public boolean equals(Object o) {
        if (!(o instanceof DocumentType)) return false;
        DocumentType other = (DocumentType) o;
        // Ignore whether one of them have added inheritance to super Document.0 type
        if (super.equals(o) && contentStructType.equals(other.contentStructType)) {
            if ((inherits.size() > 1 || other.inherits.size() > 1) ||
                (inherits.size() == 1 && other.inherits.size() == 1)) {
                return inherits.equals(other.inherits);
            }
            return !(((inherits.size() == 1) && !inherits.get(0).getDataTypeName().equals(new DataTypeName("document")))
                     || ((other.inherits.size() == 1) && !other.inherits.get(0).getDataTypeName().equals(new DataTypeName("document"))));
        }
        return false;
    }

    public int hashCode() {
        return super.hashCode() + contentStructType.hashCode() + inherits.hashCode();
    }

    @Override
    public void onSerialize(Serializer target) {
        if (target instanceof DocumentWriter) {
            ((DocumentWriter) target).write(this);
        }
        // TODO: what if it's not a DocumentWriter?
    }


    @Override
    public void visitMembers(ObjectVisitor visitor) {
        super.visitMembers(visitor);
        visitor.visit("headertype", contentStructType);
        visitor.visit("inherits", inherits);
    }
}
