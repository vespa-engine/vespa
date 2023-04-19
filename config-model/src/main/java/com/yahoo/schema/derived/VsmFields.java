// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.derived;

import com.yahoo.document.CollectionDataType;
import com.yahoo.document.DataType;
import com.yahoo.document.NumericDataType;
import com.yahoo.documentmodel.NewDocumentReferenceDataType;
import com.yahoo.document.datatypes.BoolFieldValue;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.PredicateFieldValue;
import com.yahoo.document.datatypes.Raw;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.document.datatypes.TensorFieldValue;
import com.yahoo.schema.FieldSets;
import com.yahoo.schema.Schema;
import com.yahoo.schema.document.Attribute;
import com.yahoo.schema.document.FieldSet;
import com.yahoo.schema.document.GeoPos;
import com.yahoo.schema.document.Matching;
import com.yahoo.schema.document.MatchType;
import com.yahoo.schema.document.SDDocumentType;
import com.yahoo.schema.document.SDField;
import com.yahoo.schema.processing.TensorFieldProcessor;
import com.yahoo.vespa.config.search.vsm.VsmfieldsConfig;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Vertical streaming matcher field specification
 */
public class VsmFields extends Derived implements VsmfieldsConfig.Producer {

    private final Map<String, StreamingField> fields=new LinkedHashMap<>();
    private final Map<String, StreamingDocumentType> doctypes=new LinkedHashMap<>();

    public VsmFields(Schema schema) {
        addSearchdefinition(schema);
    }

    private void addSearchdefinition(Schema schema) {
        derive(schema);
    }

    @Override
    protected void derive(SDDocumentType document, Schema schema) {
        super.derive(document, schema);
        StreamingDocumentType docType=getDocumentType(document.getName());
        if (docType == null) {
            docType = new StreamingDocumentType(document.getName(), schema.fieldSets());
            doctypes.put(document.getName(), docType);
        }
        for (Object o : document.fieldSet()) {
            derive(docType, (SDField) o);
        }
    }

    protected void derive(StreamingDocumentType document, SDField field) {
        if (field.usesStructOrMap()) {
            if (GeoPos.isAnyPos(field)) {
                StreamingField streamingField = new StreamingField(field);
                addField(streamingField.getName(), streamingField);
                addFieldToIndices(document, field.getName(), streamingField);
            }
            for (SDField structField : field.getStructFields()) {
                derive(document, structField); // Recursion
            }
        } else {
            if (! (field.doesIndexing() || field.doesSummarying() || field.doesAttributing()) )
                return;

            StreamingField streamingField = new StreamingField(field);
            addField(streamingField.getName(),streamingField);
            deriveIndices(document, field, streamingField);
        }
    }

    private void deriveIndices(StreamingDocumentType document, SDField field, StreamingField streamingField) {
        if (field.doesIndexing()) {
            addFieldToIndices(document, field.getName(), streamingField);
        } else if (field.doesAttributing()) {
            for (String indexName : field.getAttributes().keySet()) {
                addFieldToIndices(document, indexName, streamingField);
            }
        }
    }

    private void addFieldToIndices(StreamingDocumentType document, String indexName, StreamingField streamingField) {
        if (indexName.contains(".")) {
            addFieldToIndices(document, indexName.substring(0,indexName.lastIndexOf(".")), streamingField); // Recursion
        }
        document.addIndexField(indexName, streamingField.getName());
    }

    private void addField(String name, StreamingField field) {
        fields.put(name, field);
    }

    /** Returns a streaming index, or null if there is none with this name */
    public StreamingDocumentType getDocumentType(String name) {
        return doctypes.get(name);
    }

    public String getDerivedName() {
        return "vsmfields";
    }

    @Override
    public void getConfig(VsmfieldsConfig.Builder vsB) {
        for (StreamingField streamingField : fields.values()) {
            vsB.fieldspec(streamingField.getFieldSpecConfig());
        }
        for (StreamingDocumentType streamingDocType : doctypes.values()) {
            vsB.documenttype(streamingDocType.getDocTypeConfig());
        }
    }

    private static class StreamingField {

        private final String name;

        /** Whether this field does prefix matching by default */
        private final Matching matching;

        /** The type of this field */
        private final Type type;

        private final boolean isAttribute;
        private final Attribute.DistanceMetric distanceMetric;

        /** The streaming field type enumeration */
        public static class Type {

            public static Type INT8 = new Type("INT8");
            public static Type INT16 = new Type("INT16");
            public static Type INT32 = new Type("INT32");
            public static Type INT64 = new Type("INT64");
            public static Type FLOAT16 = new Type("FLOAT16");
            public static Type FLOAT = new Type("FLOAT");
            public static Type DOUBLE = new Type("DOUBLE");
            public static Type STRING = new Type("AUTOUTF8");
            public static Type BOOL = new Type("BOOL");
            public static Type UNSEARCHABLESTRING = new Type("NONE");
            public static Type GEO_POSITION = new Type("GEOPOS");
            public static Type NEAREST_NEIGHBOR = new Type("NEAREST_NEIGHBOR");

            private String searchMethod;

            private Type(String searchMethod) {
                this.searchMethod = searchMethod;
            }

            @Override
            public int hashCode() {
                return searchMethod.hashCode();
            }

            public String getSearchMethod() { return searchMethod; }

            @Override
            public boolean equals(Object other) {
                if ( ! (other instanceof Type)) return false;
                return this.searchMethod.equals(((Type)other).searchMethod);
            }

            @Override
            public String toString() {
                return "method: " + searchMethod;
            }

        }

        public StreamingField(SDField field) {
            this(field.getName(), field.getDataType(), field.getMatching(), field.doesAttributing(), getDistanceMetric(field));
        }

        private StreamingField(String name, DataType sourceType, Matching matching, boolean isAttribute, Attribute.DistanceMetric distanceMetric) {
            this.name = name;
            this.type = convertType(sourceType);
            this.matching = matching;
            this.isAttribute = isAttribute;
            this.distanceMetric = distanceMetric;
        }

        private static Attribute.DistanceMetric getDistanceMetric(SDField field) {
            var attr = field.getAttribute();
            if (attr != null) {
                return attr.distanceMetric();
            }
            return Attribute.DEFAULT_DISTANCE_METRIC;
        }

        /** Converts to the right index type from a field datatype */
        private static Type convertType(DataType fieldType) {
            FieldValue fval = fieldType.createFieldValue();
            if (fieldType.equals(DataType.FLOAT16)) {
                return Type.FLOAT16;
            } else if (fieldType.equals(DataType.FLOAT)) {
                return Type.FLOAT;
            } else if (fieldType.equals(DataType.LONG)) {
                return Type.INT64;
            } else if (fieldType.equals(DataType.DOUBLE)) {
                return Type.DOUBLE;
            } else if (fieldType.equals(DataType.BOOL)) {
                return Type.BOOL;
            } else if (fieldType.equals(DataType.BYTE)) {
                return Type.INT8;
            } else if (GeoPos.isAnyPos(fieldType)) {
                return Type.GEO_POSITION;
            } else if (fieldType instanceof NumericDataType) {
                return Type.INT32;
            } else if (fval instanceof StringFieldValue) {
                return Type.STRING;
            } else if (fval instanceof BoolFieldValue) {
                return Type.BOOL;
            } else if (fval instanceof Raw) {
                return Type.STRING;
            } else if (fval instanceof PredicateFieldValue) {
                return Type.UNSEARCHABLESTRING;
            } else if (fval instanceof TensorFieldValue) {
                var tensorType = ((TensorFieldValue) fval).getDataType().getTensorType();
                if (TensorFieldProcessor.isTensorTypeThatSupportsHnswIndex(tensorType)) {
                    return Type.NEAREST_NEIGHBOR;
                }
                return Type.UNSEARCHABLESTRING;
            } else if (fieldType instanceof CollectionDataType) {
                return convertType(((CollectionDataType) fieldType).getNestedType());
            } else if (fieldType instanceof NewDocumentReferenceDataType) {
                return Type.UNSEARCHABLESTRING;
            } else {
                throw new IllegalArgumentException("Don't know which streaming field type to convert " +
                                                   fieldType + " to");
            }
        }

        public String getName() { return name; }

        public String getMatchingName() {
            String matchingName = matching.getType().getName();
            if (matching.getType().equals(MatchType.TEXT))
                matchingName = "";
            if (matching.getType() != MatchType.EXACT) {
                if (matching.isPrefix()) {
                    matchingName = "prefix";
                } else if (matching.isSubstring()) {
                    matchingName = "substring";
                } else if (matching.isSuffix()) {
                    matchingName = "suffix";
                }
            }
            if (type != Type.STRING) {
                matchingName = "";
            }
            return matchingName;
        }

        public String getArg1() {
            if (type == Type.NEAREST_NEIGHBOR) {
                return distanceMetric.name();
            }
            return getMatchingName();
        }

        public VsmfieldsConfig.Fieldspec.Builder getFieldSpecConfig() {
            var fB = new VsmfieldsConfig.Fieldspec.Builder();
            fB.name(getName())
              .searchmethod(VsmfieldsConfig.Fieldspec.Searchmethod.Enum.valueOf(type.getSearchMethod()))
              .arg1(getArg1())
              .fieldtype(isAttribute
                             ? VsmfieldsConfig.Fieldspec.Fieldtype.ATTRIBUTE
                             : VsmfieldsConfig.Fieldspec.Fieldtype.INDEX);
            if (matching.maxLength() != null) {
                fB.maxlength(matching.maxLength());
            }
            return fB;
        }

        @Override
        public boolean equals(Object o) {
            if (o.getClass().equals(getClass())) {
                StreamingField sf = (StreamingField)o;
                return name.equals(sf.name) &&
                        matching.equals(sf.matching) &&
                        type.equals(sf.type);
            }
            return false;
        }

        @Override public int hashCode() {
            return java.util.Objects.hash(name, matching, type);
        }

    }

    private static class StreamingDocumentType {

        private final String name;
        private final Map<String, FieldSet> fieldSets = new LinkedHashMap<>();
        private final Map<String, FieldSet> userFieldSets;

        public StreamingDocumentType(String name, FieldSets fieldSets) {
            this.name=name;
            userFieldSets = fieldSets.userFieldSets();
        }

        public VsmfieldsConfig.Documenttype.Builder getDocTypeConfig() {
            VsmfieldsConfig.Documenttype.Builder dtB = new VsmfieldsConfig.Documenttype.Builder();
            dtB.name(name);
            Map<String, FieldSet> all = new LinkedHashMap<>();
            all.putAll(fieldSets);
            all.putAll(userFieldSets);
            for (Map.Entry<String, FieldSet> e : all.entrySet()) {
                VsmfieldsConfig.Documenttype.Index.Builder indB = new VsmfieldsConfig.Documenttype.Index.Builder();
                indB.name(e.getValue().getName());
                for (String field : e.getValue().getFieldNames()) {
                    indB.field(new VsmfieldsConfig.Documenttype.Index.Field.Builder().name(field));
                }
                dtB.index(indB);
            }
            return dtB;
        }

        public String getName() { return name; }

        public void addIndexField(String indexName, String fieldName) {
            FieldSet fs = fieldSets.get(indexName);
            if (fs == null) {
                fs = new FieldSet(indexName);
                fieldSets.put(indexName, fs);
            }
            fs.addFieldName(fieldName);
        }
    }

}
