// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema;

import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.application.api.FileRegistry;
import com.yahoo.config.model.api.ModelContext;
import com.yahoo.config.model.application.provider.BaseDeployLogger;
import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.document.DataTypeName;
import com.yahoo.document.Field;
import com.yahoo.schema.derived.SummaryClass;
import com.yahoo.schema.document.Attribute;
import com.yahoo.schema.document.ImmutableSDField;
import com.yahoo.schema.document.ImportedField;
import com.yahoo.schema.document.ImportedFields;
import com.yahoo.schema.document.SDDocumentType;
import com.yahoo.schema.document.SDField;
import com.yahoo.schema.document.Stemming;
import com.yahoo.schema.document.TemporaryImportedFields;
import com.yahoo.schema.document.annotation.SDAnnotationType;
import com.yahoo.searchlib.rankingexpression.Reference;
import com.yahoo.vespa.documentmodel.DocumentSummary;
import com.yahoo.vespa.documentmodel.SummaryField;

import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.stream.Stream;

/**
 * A schema contains a document type, additional fields, rank profiles and document summaries.
 *
 * @author bratseth
 */
// TODO: Make a class owned by this, for each of these responsibilities:
// Managing indexes, managing attributes, managing summary classes.
// Ensure that after the processing step, all implicit instances of the above types are explicitly represented
public class Schema implements ImmutableSchema {

    private static final String SD_DOC_FIELD_NAME = "sddocname";
    private static final List<String> RESERVED_NAMES = List.of(
            "index", "index_url", "summary", "attribute", "select_input", "host", SummaryClass.DOCUMENT_ID_FIELD,
            "position", "split_foreach", "tokenize", "if", "else", "switch", "case", SD_DOC_FIELD_NAME, "relevancy");

    /** The unique name of this schema */
    private String name;

    /** The application package this is constructed from */
    private final ApplicationPackage applicationPackage;

    /** The name of the schema this should inherit all the content of, if any */
    private final Optional<String> inherited;

    /** True if this doesn't define a search, just a document type */
    private final boolean documentsOnly;

    private Boolean rawAsBase64 = null;

    /** The stemming setting of this schema. Default is BEST. */
    private Stemming stemming = null;

    private final FieldSets fieldSets = new FieldSets(Optional.of(this));

    /** The document contained in this schema */
    private SDDocumentType documentType;

    /** The extra fields of this schema */
    private final Map<String, SDField> fields = new LinkedHashMap<>();

    private final Map<String, Index> indices = new LinkedHashMap<>();

    /** The explicitly defined summaries of this schema. _Must_ preserve order. */
    private final Map<String, DocumentSummary> summaries = new LinkedHashMap<>();

    /** External ranking expression files of this */
    private final LargeRankingExpressions largeRankingExpressions;

    /** Constants that will be available in all rank profiles. */
    // TODO: Remove on Vespa 9: Should always be in a rank profile
    private final Map<Reference, RankProfile.Constant> constants = new LinkedHashMap<>();

    // TODO: Remove on Vespa 9: Should always be in a rank profile
    private final Map<String, OnnxModel> onnxModels = new LinkedHashMap<>();

    /** All imported fields of this (and parent schemas) */
    // TODO: Use empty, not optional
    // TODO: Merge this and importedFields
    private final Optional<TemporaryImportedFields> temporaryImportedFields = Optional.of(new TemporaryImportedFields(this));
    /** The resulting processed field */
    private Optional<ImportedFields> importedFields = Optional.empty();

    private final DeployLogger deployLogger;
    private final ModelContext.Properties properties;

    private Application owner;

    /** Testing only */
    public Schema(String name, ApplicationPackage applicationPackage) {
        this(name, applicationPackage, Optional.empty(), null, new BaseDeployLogger(), new TestProperties());
    }

    public Schema(String name,
                  ApplicationPackage applicationPackage,
                  FileRegistry fileRegistry,
                  DeployLogger deployLogger,
                  ModelContext.Properties properties) {
        this(name, applicationPackage, Optional.empty(), fileRegistry, deployLogger, properties);
    }

    /**
     * Creates a schema
     *
     * @param name of the schema
     * @param inherited the schema this inherits, if any
     */
    public Schema(String name,
                  ApplicationPackage applicationPackage,
                  Optional<String> inherited,
                  FileRegistry fileRegistry,
                  DeployLogger deployLogger,
                  ModelContext.Properties properties) {
        this(inherited, applicationPackage, fileRegistry, deployLogger, properties, false);
        this.name = Objects.requireNonNull(name, "A schema must have a name");
    }

    protected Schema(ApplicationPackage applicationPackage, FileRegistry fileRegistry,
                     DeployLogger deployLogger, ModelContext.Properties properties) {
        this(Optional.empty(), applicationPackage, fileRegistry, deployLogger, properties, true);
    }

    private Schema(Optional<String> inherited,
                   ApplicationPackage applicationPackage,
                   FileRegistry fileRegistry,
                   DeployLogger deployLogger,
                   ModelContext.Properties properties,
                   boolean documentsOnly) {
        this.inherited = inherited;
        this.applicationPackage = applicationPackage;
        this.deployLogger = deployLogger;
        this.properties = properties;
        this.documentsOnly = documentsOnly;
        largeRankingExpressions = new LargeRankingExpressions(fileRegistry);
    }

    /**
     * Assigns the owner of this
     *
     * @throws IllegalStateException if an owner is already assigned
     */
    public void setOwner(Application owner) {
        if (this.owner != null)
            throw new IllegalStateException("Cannot reassign the owner of " + this);
        this.owner = owner;
    }

    protected void setName(String name) { this.name = name; }

    @Override
    public String getName() {return name; }

    /** Returns true if this only defines a document type, not a full schema */
    public boolean isDocumentsOnly() {
        return documentsOnly;
    }

    @Override
    public Optional<Schema> inherited() {
        return inherited.map(name -> owner.schemas().get(name));
    }

    /**
     * Returns true if 'raw' fields shall be presented as base64 in summary
     * Note that this is temporary and will disappear on Vespa 8 as it will become default, and only option.
     *
     * @return true if raw shall be encoded as base64 in summary
     */
    public boolean isRawAsBase64() {
        if (rawAsBase64 != null) return rawAsBase64;
        if (inherited.isEmpty()) return true;
        return requireInherited().isRawAsBase64();
    }

    public void enableRawAsBase64(boolean value) { rawAsBase64 = value; }

    /**
     * Sets the stemming default of fields. Default is ALL
     *
     * @param stemming set default stemming for this searchdefinition
     * @throws NullPointerException if this is attempted set to null
     */
    public void setStemming(Stemming stemming) {
        this.stemming = Objects.requireNonNull(stemming, "Stemming cannot be null");
    }

    /** Returns whether fields should be stemmed by default or not. Default is BEST. This is never null. */
    public Stemming getStemming() {
        if (stemming != null) return stemming;
        if (inherited.isEmpty()) return Stemming.BEST;
        return requireInherited().getStemming();
    }

    /**
     * Adds a document type which is defined in this search definition
     *
     * @param document the document type to add
     */
    public void addDocument(SDDocumentType document) {
        if (documentType != null) {
            throw new IllegalArgumentException("Schema cannot have more than one document");
        }
        documentType = document;
    }

    @Override
    public LargeRankingExpressions rankExpressionFiles() { return largeRankingExpressions; }

    public void add(RankProfile.Constant constant) {
        constants.put(constant.name(), constant);
    }

    /** Returns an unmodifiable map of the constants declared in this. */
    public Map<Reference, RankProfile.Constant> declaredConstants() { return constants; }

    /** Returns an unmodifiable map of the constants available in this. */
    @Override
    public Map<Reference, RankProfile.Constant> constants() {
        if (inherited().isEmpty()) return Collections.unmodifiableMap(constants);
        if (constants.isEmpty()) return inherited().get().constants();

        Map<Reference, RankProfile.Constant> allConstants = new LinkedHashMap<>(inherited().get().constants());
        allConstants.putAll(constants);
        return allConstants;
    }

    public void add(OnnxModel model) {
        onnxModels.put(model.getName(), model);
    }

    /** Returns an unmodifiable map of the onnx models declared in this. */
    public Map<String, OnnxModel> declaredOnnxModels() { return onnxModels; }

    /** Returns an unmodifiable map of the onnx models available in this. */
    @Override
    public Map<String, OnnxModel> onnxModels() {
        if (inherited().isEmpty()) return Collections.unmodifiableMap(onnxModels);
        if (onnxModels.isEmpty()) return inherited().get().onnxModels();

        Map<String, OnnxModel> allModels = new LinkedHashMap<>(inherited().get().onnxModels());
        allModels.putAll(onnxModels);
        return allModels;
    }

    public Optional<TemporaryImportedFields> temporaryImportedFields() {
        return temporaryImportedFields;
    }

    public Optional<ImportedFields> importedFields() {
        return importedFields;
    }

    public void setImportedFields(ImportedFields importedFields) {
        this.importedFields = Optional.of(importedFields);
    }

    @Override
    public Stream<ImmutableSDField> allImportedFields() {
        return importedFields
                .map(fields -> fields.fields().values().stream())
                .orElse(Stream.empty())
                .map(field -> field.asImmutableSDField());
    }

    @Override
    public ImmutableSDField getField(String name) {
        ImmutableSDField field = getConcreteField(name);
        if (field != null) return field;
        return allImportedFields()
                .filter(f -> f.getName().equals(name))
                .findFirst()
                .orElse(null);
    }

    @Override
    public List<ImmutableSDField> allFieldsList() {
        List<ImmutableSDField> all = new ArrayList<>();
        all.addAll(extraFieldList());
        for (Field field : documentType.fieldSet()) {
            all.add((ImmutableSDField) field);
        }
        if (importedFields.isPresent()) {
            for (ImportedField imported : importedFields.get().fields().values()) {
                all.add(imported.asImmutableSDField());
            }
        }
        return all;
    }

    /**
     * Gets a document from this search definition
     *
     * @param name the name of the document to return
     * @return the contained or used document type, or null if there is no such document
     */
    public SDDocumentType getDocument(String name) {
        if (documentType != null && name.equals(documentType.getName())) {
            return documentType;
        }
        return null;
    }

    /**
     * @return true if the document has been added.
     */
    public boolean hasDocument() {
        return documentType != null;
    }

    /**
     * @return The document in this search.
     */
    @Override
    public SDDocumentType getDocument() {
        return documentType;
    }

    /**
     * Returns a list of all the fields of this search definition, that is all fields in all documents, in the documents
     * they inherit, and all extra fields. The caller receives ownership to the list - subsequent changes to it will not
     * impact this
     */
    @Override
    public List<SDField> allConcreteFields() {
        List<SDField> allFields = new ArrayList<>();
        allFields.addAll(extraFieldList());
        for (Field field : documentType.fieldSet()) {
            allFields.add((SDField)field);
        }
        return allFields;
    }

    /**
     * Returns the content of a ranking expression file
     */
    @Override
    public Reader getRankingExpression(String fileName) {
        return applicationPackage.getRankingExpression(fileName);
    }

    public Application application() { return owner; }

    @Override
    public ApplicationPackage applicationPackage() { return applicationPackage; }

    @Override
    public DeployLogger getDeployLogger() { return deployLogger; }

    @Override
    public ModelContext.Properties getDeployProperties() { return properties; }

    /**
     * Returns a field defined in this search definition or one if its documents. Fields in this search definition takes
     * precedence over document fields having the same name
     *
     * @param name of the field
     * @return the SDField representing the field
     */
    @Override
    public SDField getConcreteField(String name) {
        SDField field = getExtraField(name);
        if (field != null) return field;

        return (SDField) documentType.getField(name);
    }

    /**
     * Returns a field defined in one of the documents of this search definition.
     * This does not include the extra fields defined outside the document
     * (those accessible through the getExtraField() method).
     *
     * @param name the name of the field to return
     * @return the named field, or null if not found
     */
    public SDField getDocumentField(String name) {
        return (SDField) documentType.getField(name);
    }

    /**
     * Adds an extra field of this search definition not contained in a document
     *
     * @param field to add to the schemas list of external fields
     */
    public void addExtraField(SDField field) {
        if (fields.containsKey(field.getName())) {
            deployLogger.logApplicationPackage(Level.WARNING, "Duplicate field " + field.getName() + " in search definition " + getName());
        } else {
            field.setIsExtraField(true);
            fields.put(field.getName(), field);
        }
    }

    public Collection<SDField> extraFieldList() {
        if (inherited.isEmpty()) return fields.values();
        var fields = new HashSet<>(requireInherited().extraFieldList());
        fields.addAll(this.fields.values());
        return fields;
    }

    public Collection<SDField> allExtraFields() {
        Map<String, SDField> extraFields = new TreeMap<>();
        if (inherited.isPresent())
            requireInherited().allExtraFields().forEach(field -> extraFields.put(field.getName(), field));
        for (Field field : documentType.fieldSet()) {
            SDField sdField = (SDField) field;
            if (sdField.isExtraField()) {
                extraFields.put(sdField.getName(), sdField);
            }
        }
        for (SDField field : extraFieldList()) {
            extraFields.put(field.getName(), field);
        }
        return extraFields.values();
    }

    /**
     * Returns a field by name, or null if it is not present
     *
     * @param fieldName the name of the external field to get
     * @return the SDField of this name
     */
    public SDField getExtraField(String fieldName) {
        SDField field = fields.get(fieldName);
        if (field != null) return field;
        if (inherited.isEmpty()) return null;
        return requireInherited().getExtraField(fieldName);
    }

    /**
     * Adds an explicitly defined index to this search definition
     *
     * @param index the index to add
     */
    public void addIndex(Index index) {
        indices.put(index.getName(), index);
    }

    /**
     * Returns an index, or null if no index with this name has had some <b>explicit settings</b> applied. Even if
     * this returns null, the index may be implicitly defined by an indexing statement. This will return the
     * index whether it is defined on this schema or on one of its fields.
     *
     * @param name the name of the index to get
     * @return the index requested
     */
    @Override
    public Index getIndex(String name) {
        List<Index> sameIndices = new ArrayList<>(1);

        getSchemaIndex(name).ifPresent(sameIndices::add);

        for (ImmutableSDField field : allConcreteFields()) {
            if (field.getIndex(name) != null)
                sameIndices.add(field.getIndex(name));
        }
        if (sameIndices.size() == 0) return null;
        if (sameIndices.size() == 1) return sameIndices.get(0);
        return consolidateIndices(sameIndices);
    }

    /** Returns the schema level index of this name, in this or any inherited schema, if any */
    Optional<Index> getSchemaIndex(String name) {
        if (indices.containsKey(name)) return Optional.of(indices.get(name));
        if (inherited.isPresent()) return requireInherited().getSchemaIndex(name);
        return Optional.empty();
    }

    public boolean existsIndex(String name) {
        if (indices.get(name) != null)
            return true;
        if (inherited.isPresent() && requireInherited().existsIndex(name))
            return true;
        for (ImmutableSDField field : allConcreteFields()) {
            if (field.existsIndex(name))
                return true;
        }
        return false;
    }

    /**
     * Consolidates a set of index settings for the same index into one
     *
     * @param indices the list of indexes to consolidate
     * @return the consolidated index
     */
    private Index consolidateIndices(List<Index> indices) {
        Index first = indices.get(0);
        Index consolidated = new Index(first.getName());
        consolidated.setRankType(first.getRankType());
        consolidated.setType(first.getType());
        for (Index current : indices) {
            if (current.isPrefix()) {
                consolidated.setPrefix(true);
            }
            if (current.useInterleavedFeatures()) {
                consolidated.setInterleavedFeatures(true);
            }

            if (consolidated.getRankType() == null) {
                consolidated.setRankType(current.getRankType());
            } else {
                if (current.getRankType() != null && consolidated.getRankType() != current.getRankType())
                    deployLogger.logApplicationPackage(Level.WARNING, "Conflicting rank type settings for " +
                                                                      first.getName() + " in " + this + ", using " +
                                                                      consolidated.getRankType());
            }

            for (Iterator<String> j = current.aliasIterator(); j.hasNext();) {
                consolidated.addAlias(j.next());
            }
        }
        return consolidated;
    }

    /** All explicitly defined indices, both on this schema itself (returned first) and all its fields */
    @Override
    public List<Index> getExplicitIndices() {
        List<Index> allIndices = new ArrayList<>(indices.values());

        if (inherited.isPresent()) {
            for (Index inheritedIndex : requireInherited().getExplicitIndices()) {
                if ( ! indices.containsKey(inheritedIndex.getName())) // child redefinitions shadows parents
                    allIndices.add(inheritedIndex);
            }
        }

        for (ImmutableSDField field : allConcreteFields())
            allIndices.addAll(field.getIndices().values());

        return Collections.unmodifiableList(allIndices);
    }

    /** Adds an explicitly defined summary to this search definition */
    public void addSummary(DocumentSummary summary) {
        summaries.put(summary.getName(), summary);
    }

    /**
     * Returns a summary class defined by this search definition, or null if no summary with this name is defined.
     * The default summary, named "default" is always present.
     */
    public DocumentSummary getSummary(String name) {
        var summary = summaries.get(name);
        if (summary != null) return summary;
        if (inherited.isEmpty()) return null;
        return requireInherited().getSummary(name);
    }

    /**
     * Returns the first explicit instance found of a summary field with this name, or null if not present (implicitly
     * or explicitly) in any summary class.
     */
    public SummaryField getSummaryField(String name) {
        for (DocumentSummary summary : summaries.values()) {
            SummaryField summaryField = summary.getSummaryField(name);
            if (summaryField != null) {
                return summaryField;
            }
        }
        if (inherited.isEmpty()) return null;
        return requireInherited().getSummaryField(name);
    }

    /**
     * Returns the first explicit instance found of a summary field with this name, or null if not present explicitly in
     * any summary class
     *
     * @param name the name of the explicit summary field to get.
     * @return the SummaryField found.
     */
    public SummaryField getExplicitSummaryField(String name) {
        for (DocumentSummary summary : summaries.values()) {
            SummaryField summaryField = summary.getSummaryField(name);
            if (summaryField != null && !summaryField.isImplicit())
                return summaryField;
        }
        if (inherited.isEmpty()) return null;
        return requireInherited().getExplicitSummaryField(name);
    }

    /**
     * Summaries defined by fields of this search definition. The default summary, named "default", is always the first
     * one in the returned iterator.
     */
    public Map<String, DocumentSummary> getSummaries() {
        // Shortcuts
        if (inherited.isEmpty()) return summaries;
        if (summaries.isEmpty()) return requireInherited().getSummaries();

        var allSummaries = new LinkedHashMap<>(requireInherited().getSummaries());
        allSummaries.putAll(summaries);
        return allSummaries;
    }

    /** Returns the summaries defines in this only, not any that are inherited. */
    public Map<String, DocumentSummary> getSummariesInThis() { return Collections.unmodifiableMap(summaries); }

    /**
     * Returns all summary fields, of all document summaries, which has the given field as source.
     * The list becomes owned by the receiver.
     *
     * @param field the source field
     * @return the list of summary fields found
     */
    @Override
    public List<SummaryField> getSummaryFields(ImmutableSDField field) {
        List<SummaryField> summaryFields = inherited.isPresent()
            ? requireInherited().getSummaryFields(field)
            : new java.util.ArrayList<>();
        for (DocumentSummary documentSummary : summaries.values()) {
            for (SummaryField summaryField : documentSummary.getSummaryFields().values()) {
                if (summaryField.hasSource(field.getName())) {
                    boolean wanted = true;
                    for (var already : summaryFields) {
                        if (summaryField == already) wanted = false;
                    }
                    if (wanted) {
                        summaryFields.add(summaryField);
                    }
                }
            }
        }
        return summaryFields;
    }

    /**
     * Returns one summary field for each summary field name. If there are multiple summary fields with the same
     * name, the last one will be used. Multiple fields of the same name should all have the same content in a valid
     * search definition, except from the destination set. So this method can be used for all summary handling except
     * processing the destination set. The map becomes owned by the receiver.
     */
    public Map<String, SummaryField> getUniqueNamedSummaryFields() {
        Map<String, SummaryField> summaryFields = inherited.isPresent() ? requireInherited().getUniqueNamedSummaryFields()
                                                                        : new java.util.LinkedHashMap<>();
        for (DocumentSummary documentSummary : summaries.values()) {
            for (SummaryField summaryField : documentSummary.getSummaryFields().values()) {
                summaryFields.put(summaryField.getName(), summaryField);
            }
        }
        return summaryFields;
    }

    /** Returns the first occurrence of an attribute having this name, or null if none */
    public Attribute getAttribute(String name) {
        for (ImmutableSDField field : allConcreteFields()) {
            Attribute attribute = field.getAttributes().get(name);
            if (attribute != null) {
                return attribute;
            }
        }
        return null;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Schema)) {
            return false;
        }

        Schema other = (Schema)o;
        return getName().equals(other.getName());
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @Override
    public String toString() {
        return "schema '" + getName() + "'";
    }

    public boolean isAccessingDiskSummary(SummaryField field) {
        if (!field.getTransform().isInMemory()) return true;
        if (field.getSources().size() == 0) return isAccessingDiskSummary(getName());
        for (SummaryField.Source source : field.getSources()) {
            if (isAccessingDiskSummary(source.getName()))
                return true;
        }
        return false;
    }

    private boolean isAccessingDiskSummary(String source) {
        SDField field = getConcreteField(source);
        if (field == null) return false;
        if (field.doesSummarying() && !field.doesAttributing()) return true;
        return false;
    }

    public FieldSets fieldSets() {  return fieldSets; }

    private Schema inheritedSchema = null;

    public void setInheritedSchema(Schema value) {
        inheritedSchema = value;
    }

    /** Returns the schema inherited by this, or throws if none */
    private Schema requireInherited() {
        if (inheritedSchema != null) return inheritedSchema;
        return owner.schemas().get(inherited.get());
    }

    /**
     * For adding structs defined in document scope
     *
     * @param dt the struct to add
     * @return self, for chaining
     */
    public Schema addType(SDDocumentType dt) {
        documentType.addType(dt); // TODO This is a very very dirty thing. It must go
        return this;
    }

    public Schema addAnnotation(SDAnnotationType dt) {
        documentType.addAnnotation(dt);
        return this;
    }

    public void validate(DeployLogger logger) {
        if (inherited.isPresent()) {
            if (! owner.schemas().containsKey(inherited.get()))
                throw new IllegalArgumentException(this + " inherits '" + inherited.get() +
                                                   "', but this schema does not exist");

            // Require schema and document type inheritance to be consistent to keep things simple
            // And require it to be explicit so we have the option to support other possibilities later
            var parentDocument = owner.schemas().get(inherited.get()).getDocument();
            if ( ! getDocument().inheritedTypes().containsKey(new DataTypeName(parentDocument.getName())))
                throw new IllegalArgumentException(this + " inherits '" + inherited.get() +
                                                   "', but its document type does not inherit the parent's document type");
        }
        for (var summary : summaries.values())
            summary.validate(logger);
    }

    /** Returns true if the given field name is a reserved name */
    public static boolean isReservedName(String name) {
        return RESERVED_NAMES.contains(name);
    }

}
