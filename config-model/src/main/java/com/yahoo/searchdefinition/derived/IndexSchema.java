// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.derived;

import com.yahoo.document.ArrayDataType;
import com.yahoo.document.DataType;
import com.yahoo.document.Field;
import com.yahoo.document.StructuredDataType;
import com.yahoo.document.TensorDataType;
import com.yahoo.document.WeightedSetDataType;
import com.yahoo.searchdefinition.Search;
import com.yahoo.searchdefinition.document.BooleanIndexDefinition;
import com.yahoo.searchdefinition.document.FieldSet;
import com.yahoo.searchdefinition.document.ImmutableSDField;
import com.yahoo.vespa.config.search.IndexschemaConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Deriver of indexschema config containing information of all text index fields with name and data type.
 *
 * @author geirst
 */
public class IndexSchema extends Derived implements IndexschemaConfig.Producer {

    private final List<IndexField> fields = new ArrayList<>();
    private final Map<String, FieldCollection> collections = new LinkedHashMap<>();
    private final Map<String, FieldSet> fieldSets = new LinkedHashMap<>();

    public IndexSchema(Search search) {
        fieldSets.putAll(search.fieldSets().userFieldSets());
        derive(search);
    }

    public boolean containsField(String fieldName) {
        return fields.stream().anyMatch(field -> field.getName().equals(fieldName));
    }

    @Override
    protected void derive(Search search) {
        super.derive(search);
    }

    private boolean isTensorField(ImmutableSDField field) {
        return field.getDataType() instanceof TensorDataType;
    }

    private void deriveIndexFields(ImmutableSDField field, Search search) {
        // Note: Indexes for tensor fields are NOT part of the index schema for text fields.
        if ((!field.doesIndexing() && !field.isIndexStructureField()) ||
                isTensorField(field))
        {
            return;
        }
        List<Field> lst = flattenField(field.asField());
        if (lst.isEmpty()) {
            return;
        }
        String fieldName = field.getName();
        for (Field flatField : lst) {
            deriveIndexFields(flatField, search);
        }
        if (lst.size() > 1) {
            FieldSet fieldSet = new FieldSet(fieldName);
            for (Field flatField : lst) {
                fieldSet.addFieldName(flatField.getName());
            }
            fieldSets.put(fieldName, fieldSet);
        }
    }

    private void deriveIndexFields(Field field, Search search) {
        IndexField toAdd = new IndexField(field.getName(), Index.convertType(field.getDataType()), field.getDataType());
        com.yahoo.searchdefinition.Index definedIndex = search.getIndex(field.getName());
        if (definedIndex != null) {
            toAdd.setIndexSettings(definedIndex);
        }
        fields.add(toAdd);
        addFieldToCollection(field.getName(), field.getName()); // implicit
    }

    private FieldCollection getCollection(String collectionName) {
        FieldCollection retval = collections.get(collectionName);
        if (retval == null) {
            collections.put(collectionName, new FieldCollection(collectionName));
            return collections.get(collectionName);
        }
        return retval;
    }

    private void addFieldToCollection(String fieldName, String collectionName) {
        FieldCollection collection = getCollection(collectionName);
        collection.fields.add(fieldName);
    }

    @Override
    protected void derive(ImmutableSDField field, Search search) {
        if (field.usesStructOrMap()) {
            return; // unsupported
        }
        deriveIndexFields(field, search);
    }

    @Override
    protected String getDerivedName() {
        return "indexschema";
    }

    @Override
    public void getConfig(IndexschemaConfig.Builder icB) {
        for (int i = 0; i < fields.size(); ++i) {
            IndexField f = fields.get(i);
            IndexschemaConfig.Indexfield.Builder ifB = new IndexschemaConfig.Indexfield.Builder()
                .name(f.getName())
                .datatype(IndexschemaConfig.Indexfield.Datatype.Enum.valueOf(f.getType()))
                .prefix(f.hasPrefix())
                .phrases(f.hasPhrases())
                .positions(f.hasPositions())
                .interleavedfeatures(f.useInterleavedFeatures());
            if (!f.getCollectionType().equals("SINGLE")) {
                ifB.collectiontype(IndexschemaConfig.Indexfield.Collectiontype.Enum.valueOf(f.getCollectionType()));
            }
            icB.indexfield(ifB);
        }
        for (FieldSet fieldSet : fieldSets.values()) {
            IndexschemaConfig.Fieldset.Builder fsB = new IndexschemaConfig.Fieldset.Builder()
                .name(fieldSet.getName());
            for (String f : fieldSet.getFieldNames()) {
                fsB.field(new IndexschemaConfig.Fieldset.Field.Builder()
                        .name(f));
            }
            icB.fieldset(fsB);
        }
    }

    @SuppressWarnings("deprecation")
    static List<Field> flattenField(Field field) {
        DataType fieldType = field.getDataType();
        if (fieldType.getPrimitiveType() != null){
            return Collections.singletonList(field);
        }
        if (fieldType instanceof ArrayDataType) {
            List<Field> ret = new LinkedList<>();
            Field innerField = new Field(field.getName(), ((ArrayDataType)fieldType).getNestedType());
            for (Field flatField : flattenField(innerField)) {
                ret.add(new Field(flatField.getName(), DataType.getArray(flatField.getDataType())));
            }
            return ret;
        }
        if (fieldType instanceof StructuredDataType) {
            List<Field> ret = new LinkedList<>();
            String fieldName = field.getName();
            for (Field childField : ((StructuredDataType)fieldType).getFields()) {
                for (Field flatField : flattenField(childField)) {
                    ret.add(new Field(fieldName + "." + flatField.getName(), flatField));
                }
            }
            return ret;
        }
        throw new UnsupportedOperationException(fieldType.getName());
    }

    public List<IndexField> getFields() {
        return fields;
    }

    /**
     * Representation of an index field with name and data type.
     */
    public static class IndexField {
        private String name;
        private Index.Type type;
        private com.yahoo.searchdefinition.Index.Type sdType; // The index type in "user intent land"
        private DataType sdFieldType;
        private boolean prefix = false;
        private boolean phrases = false; // TODO dead, but keep a while to ensure config compatibility?
        private boolean positions = true;// TODO dead, but keep a while to ensure config compatibility?
        private BooleanIndexDefinition boolIndex = null;
        // Whether the posting lists of this index field should have interleaved features (num occs, field length) in document id stream.
        private boolean interleavedFeatures = false;

        public IndexField(String name, Index.Type type, DataType sdFieldType) {
            this.name = name;
            this.type = type;
            this.sdFieldType = sdFieldType;
        }
        public void setIndexSettings(com.yahoo.searchdefinition.Index index) {
            if (type.equals(Index.Type.TEXT)) {
                prefix = index.isPrefix();
                interleavedFeatures = index.useInterleavedFeatures();
            }
            sdType = index.getType();
            boolIndex = index.getBooleanIndexDefiniton();
        }
        public String getName() { return name; }
        public Index.Type getRawType() { return type; }
        public String getType() {
            return type.equals(Index.Type.INT64)
                    ? "INT64" : "STRING";
        }
	    public String getCollectionType() {
	        return (sdFieldType == null)
                    ? "SINGLE"
                    : (sdFieldType instanceof WeightedSetDataType)
		                ? "WEIGHTEDSET"
                        : (sdFieldType instanceof ArrayDataType)
                            ? "ARRAY"
                            : "SINGLE";
	    }
        public boolean hasPrefix() { return prefix; }
        public boolean hasPhrases() { return phrases; }
        public boolean hasPositions() { return positions; }
        public boolean useInterleavedFeatures() { return interleavedFeatures; }

        public BooleanIndexDefinition getBooleanIndexDefinition() {
            return boolIndex;
        }

        /**
         * The user set index type
         * @return the type
         */
        public com.yahoo.searchdefinition.Index.Type getSdType() {
            return sdType;
        }
    }

    /**
     * Representation of a collection of fields (aka index, physical view).
     */
    @SuppressWarnings({ "UnusedDeclaration" })
    private static class FieldCollection {

        private final String name;
        private final List<String> fields = new ArrayList<>();

        FieldCollection(String name) {
            this.name = name;
        }
    }
}
