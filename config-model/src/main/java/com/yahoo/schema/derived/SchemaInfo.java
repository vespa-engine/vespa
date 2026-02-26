// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.derived;

import com.yahoo.document.ArrayDataType;
import com.yahoo.document.DataType;
import com.yahoo.document.MapDataType;
import com.yahoo.document.PrimitiveDataType;
import com.yahoo.document.ReferenceDataType;
import com.yahoo.document.StructuredDataType;
import com.yahoo.document.TensorDataType;
import com.yahoo.document.WeightedSetDataType;
import com.yahoo.document.annotation.AnnotationReferenceDataType;
import com.yahoo.documentmodel.NewDocumentReferenceDataType;
import com.yahoo.schema.document.Attribute;
import com.yahoo.schema.document.FieldSet;
import com.yahoo.schema.document.ImmutableSDField;
import com.yahoo.search.config.SchemaInfoConfig;
import com.yahoo.schema.Index;
import com.yahoo.schema.RankProfile;
import com.yahoo.schema.RankProfileRegistry;
import com.yahoo.schema.Schema;
import com.yahoo.searchlib.rankingexpression.Reference;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Information about a schema.
 *
 * @author bratseth
 */
@SuppressWarnings("removal")
public final class SchemaInfo extends Derived {

    private final Schema schema;

    // Info about profiles needed in memory after build.
    // The rank profile registry itself is not kept around due to its size.
    private final Map<String, RankProfileInfo> rankProfiles;

    private final Summaries summaries;
    private final IndexMode indexMode;

    public enum IndexMode {INDEX, STREAMING, STORE_ONLY}

    public SchemaInfo(Schema schema, String indexMode, RankProfileRegistry rankProfileRegistry, Summaries summaries) {
        this(schema, indexMode(indexMode), rankProfileRegistry, summaries);
    }
    public SchemaInfo(Schema schema, IndexMode indexMode, RankProfileRegistry rankProfileRegistry, Summaries summaries) {
        this.schema = schema;
        this.rankProfiles = Collections.unmodifiableMap(toRankProfiles(rankProfileRegistry.rankProfilesOf(schema)));
        this.summaries = summaries;
        this.indexMode = indexMode;
    }

    private static IndexMode indexMode(String mode) {
        if (mode == null) return IndexMode.INDEX;
        return switch (mode) {
            case "index" -> IndexMode.INDEX;
            case "streaming" -> IndexMode.STREAMING;
            case "store-only" -> IndexMode.STORE_ONLY;
            default -> IndexMode.STORE_ONLY;
        };
    }

    public IndexMode getIndexMode() { return indexMode; }

    public String name() { return schema.getName(); }

    @Override
    public String getDerivedName() { return "schema-info"; }

    public Schema fullSchema() { return schema; }

    public Map<String, RankProfileInfo> rankProfiles() { return rankProfiles; }

    private Map<String, RankProfileInfo> toRankProfiles(Collection<RankProfile> rankProfiles) {
        Map<String, RankProfileInfo> rankProfileInfos = new LinkedHashMap<>();
        rankProfiles.forEach(profile -> rankProfileInfos.put(profile.name(), new RankProfileInfo(profile)));
        return rankProfileInfos;
    }

    public void getConfig(SchemaInfoConfig.Builder builder) {
        var schemaBuilder = new SchemaInfoConfig.Schema.Builder();
        schemaBuilder.name(schema.getName());
        addFieldsConfig(schemaBuilder);
        addFieldSetConfig(schemaBuilder);
        addSummaryConfig(schemaBuilder);
        addRankProfilesConfig(schemaBuilder);
        builder.schema(schemaBuilder);
    }

    public void export(String toDirectory) throws IOException {
        var builder = new SchemaInfoConfig.Builder();
        getConfig(builder);
        export(toDirectory, builder.build());
    }

    private void addFieldsConfig(SchemaInfoConfig.Schema.Builder schemaBuilder) {
        for (var field : schema.allFieldsList()) {
            addFieldConfig(field, schemaBuilder);
            for (var index : field.getIndices().values()) {
                if ( ! index.getName().equals(field.getName())) // additional index
                    addFieldConfig(index, field.getDataType(), schemaBuilder);
            }
            for (var attribute : field.getAttributes().values()) {
                if ( ! attribute.getName().equals(field.getName())) // additional attribute
                    addFieldConfig(attribute, field.getDataType(), schemaBuilder);
            }
        }
    }

    private void addFieldConfig(ImmutableSDField field, SchemaInfoConfig.Schema.Builder schemaBuilder) {
        var fieldBuilder = new SchemaInfoConfig.Schema.Field.Builder();
        fieldBuilder.name(field.getName());
        fieldBuilder.type(toTypeSpec(field.getDataType()));
        for (var alias : field.getAliasToName().entrySet()) {
            if (alias.getValue().equals(field.getName()))
                fieldBuilder.alias(alias.getKey());
        }
        fieldBuilder.attribute(field.doesAttributing());
        fieldBuilder.index(field.doesIndexing());
        fieldBuilder.bitPacked(field.doesBitPacking());
        schemaBuilder.field(fieldBuilder);
    }

    // TODO: Make fields and indexes 1-1 so that this can be removed
    private void addFieldConfig(Index index, DataType type, SchemaInfoConfig.Schema.Builder schemaBuilder) {
        var fieldBuilder = new SchemaInfoConfig.Schema.Field.Builder();
        fieldBuilder.name(index.getName());
        fieldBuilder.type(toTypeSpec(type));
        for (Iterator<String> i = index.aliasIterator(); i.hasNext(); )
            fieldBuilder.alias(i.next());
        fieldBuilder.attribute(false);
        fieldBuilder.index(true);
        fieldBuilder.bitPacked(false);
        schemaBuilder.field(fieldBuilder);
    }

    // TODO: Make fields and attributes 1-1 so that this can be removed
    private void addFieldConfig(Attribute attribute, DataType type, SchemaInfoConfig.Schema.Builder schemaBuilder) {
        var fieldBuilder = new SchemaInfoConfig.Schema.Field.Builder();
        fieldBuilder.name(attribute.getName());
        fieldBuilder.type(toTypeSpec(type));
        for (var alias : attribute.getAliases())
            fieldBuilder.alias(alias);
        fieldBuilder.attribute(true);
        fieldBuilder.index(false);
        fieldBuilder.bitPacked(false);
        schemaBuilder.field(fieldBuilder);
    }

    private void addFieldSetConfig(SchemaInfoConfig.Schema.Builder schemaBuilder) {
        for (var fieldSet : schema.fieldSets().builtInFieldSets().values())
            addFieldSetConfig(fieldSet, schemaBuilder);
        for (var fieldSet : schema.fieldSets().userFieldSets().values())
            addFieldSetConfig(fieldSet, schemaBuilder);
    }

    private void addFieldSetConfig(FieldSet fieldSet, SchemaInfoConfig.Schema.Builder schemaBuilder) {
        var fieldSetBuilder = new SchemaInfoConfig.Schema.Fieldset.Builder();
        fieldSetBuilder.name(fieldSet.getName());
        for (String fieldName : fieldSet.getFieldNames())
            fieldSetBuilder.field(fieldName);
        schemaBuilder.fieldset(fieldSetBuilder);
    }

    private void addSummaryConfig(SchemaInfoConfig.Schema.Builder schemaBuilder) {
        for (var summary : summaries.asList()) {
            var summaryBuilder = new SchemaInfoConfig.Schema.Summaryclass.Builder();
            summaryBuilder.name(summary.getName());
            for (var field : summary.fields().values()) {
                var fieldsBuilder = new SchemaInfoConfig.Schema.Summaryclass.Fields.Builder();
                fieldsBuilder.name(field.getName())
                             .type(field.getType().getName())
                             .dynamic(SummaryClass.commandRequiringQuery(field.getCommand()) ||
                                      SummaryClass.elementsSelectorRequiringQuery(field.getElementsSelector()));
                summaryBuilder.fields(fieldsBuilder);
            }
            schemaBuilder.summaryclass(summaryBuilder);
        }
    }

    private void addRankProfilesConfig(SchemaInfoConfig.Schema.Builder schemaBuilder) {
        for (RankProfileInfo rankProfile : rankProfiles().values()) {
            var rankProfileConfig = new SchemaInfoConfig.Schema.Rankprofile.Builder()
                    .name(rankProfile.name())
                    .hasSummaryFeatures(rankProfile.hasSummaryFeatures())
                    .hasRankFeatures(rankProfile.hasRankFeatures())
                    .significance(new SchemaInfoConfig.Schema.Rankprofile.Significance.Builder()
                                          .useModel(rankProfile.useSignificanceModel()));
            rankProfile.getKeepRankCount().ifPresent(rankProfileConfig::keepRankCount);
            rankProfile.getTotalKeepRankCount().ifPresent(rankProfileConfig::totalKeepRankCount);
            rankProfile.getRerankCount().ifPresent(rankProfileConfig::rerankCount);
            rankProfile.getTotalRerankCount().ifPresent(rankProfileConfig::totalRerankCount);
            for (var input : rankProfile.inputs().entrySet()) {
                var inputConfig = new SchemaInfoConfig.Schema.Rankprofile.Input.Builder();
                inputConfig.name(input.getKey().toString());
                inputConfig.type(input.getValue().type().toString());
                rankProfileConfig.input(inputConfig);
            }
            schemaBuilder.rankprofile(rankProfileConfig);
        }
    }

    /** Returns this type as a spec on the form following "field [name] type " in schemas. */
    private String toTypeSpec(DataType dataType) {
        if (dataType instanceof PrimitiveDataType)
            return dataType.getName();
        if (dataType instanceof AnnotationReferenceDataType annotationType)
            return "annotationreference<" + annotationType.getAnnotationType().getName() + ">";
        if (dataType instanceof ArrayDataType arrayType)
            return "array<" + toTypeSpec(arrayType.getNestedType()) + ">";
        if (dataType instanceof MapDataType mapType)
            return "map<" + toTypeSpec(mapType.getKeyType()) + "," + toTypeSpec(mapType.getValueType()) + ">";
        if (dataType instanceof ReferenceDataType referenceType)
            return "reference<" + toTypeSpec(referenceType.getTargetType()) + ">";
        if (dataType instanceof NewDocumentReferenceDataType referenceType)
            return "reference<" + toTypeSpec(referenceType.getTargetType()) + ">";
        if (dataType instanceof StructuredDataType structType)
            return structType.getName();
        if (dataType instanceof TensorDataType tensorType)
            return tensorType.getTensorType().toString();
        if (dataType instanceof WeightedSetDataType weightedSetDataType)
            return "weightedset<" + toTypeSpec(weightedSetDataType.getNestedType()) + ">";
        throw new IllegalArgumentException("Unknown data type " + dataType + " class " + dataType.getClass());
    }

    /** A store of a *small* (in memory) amount of rank profile info. */
    public static final class RankProfileInfo {

        private final String name;
        private final boolean hasSummaryFeatures;
        private final boolean hasRankFeatures;
        private final Optional<Integer> keepRankCount;
        private final Optional<Integer> totalKeepRankCount;
        private final Optional<Integer> rerankCount;
        private final Optional<Integer> totalRerankCount;
        private final boolean useSignificanceModel;
        private final Map<Reference, RankProfile.Input> inputs;

        public RankProfileInfo(RankProfile profile) {
            this.name = profile.name();
            this.hasSummaryFeatures =  ! profile.getSummaryFeatures().isEmpty();
            this.hasRankFeatures =  ! profile.getRankFeatures().isEmpty();
            this.inputs = profile.inputs();
            this.keepRankCount = profile.getKeepRankCount();
            this.totalKeepRankCount = profile.getTotalKeepRankCount();
            this.rerankCount = profile.getRerankCount();
            this.totalRerankCount = profile.getTotalRerankCount();
            useSignificanceModel = profile.useSignificanceModel();
        }

        public String name() { return name; }
        public boolean hasSummaryFeatures() { return hasSummaryFeatures; }
        public boolean hasRankFeatures() { return hasRankFeatures; }
        public Optional<Integer> getKeepRankCount() { return keepRankCount; }
        public Optional<Integer> getTotalKeepRankCount() { return totalKeepRankCount; }
        public Optional<Integer> getRerankCount() { return rerankCount; }
        public Optional<Integer> getTotalRerankCount() { return totalRerankCount; }
        public boolean useSignificanceModel() { return useSignificanceModel; }
        public Map<Reference, RankProfile.Input> inputs() { return inputs; }

    }

}
