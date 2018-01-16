// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition;

import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.document.Field;
import com.yahoo.searchdefinition.document.Attribute;
import com.yahoo.searchdefinition.document.ImmutableImportedSDField;
import com.yahoo.searchdefinition.document.ImmutableSDField;
import com.yahoo.searchdefinition.document.ImportedFields;
import com.yahoo.searchdefinition.document.SDDocumentType;
import com.yahoo.searchdefinition.document.SDField;
import com.yahoo.searchdefinition.document.Stemming;
import com.yahoo.searchdefinition.document.TemporaryImportedFields;
import com.yahoo.searchdefinition.document.annotation.SDAnnotationType;
import com.yahoo.vespa.documentmodel.DocumentSummary;
import com.yahoo.vespa.documentmodel.SummaryField;

import java.io.Reader;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * <p>A search definition describes (or uses) some document types, defines how these are turned into a relevancy tuned
 * index through indexing and how data from documents should be served at search time.</p> <p>The identity of this
 * class is its name.</p>
 *
 * @author bratseth
 */
// TODO: Make a class owned by this, for each of these responsibilities:
// Managing indexes, managing attributes, managing summary classes.
// Ensure that after the processing step, all implicit instances of the above types are explicitly represented
public class Search implements Serializable, ImmutableSearch {

    private static final Logger log = Logger.getLogger(Search.class.getName());
    private static final String SD_DOC_FIELD_NAME = "sddocname";
    private static final List<String> RESERVED_NAMES = Arrays.asList(
            "index", "index_url", "summary", "attribute", "select_input", "host", "documentid",
            "position", "split_foreach", "tokenize", "if", "else", "switch", "case", SD_DOC_FIELD_NAME, "relevancy");

    /**
     * @return True if the given field name is a reserved name.
     */
    public static boolean isReservedName(String name) {
        return RESERVED_NAMES.contains(name);
    }

    // Field sets
    private FieldSets fieldSets = new FieldSets();

    // Whether or not this object has been processed.
    private boolean processed;

    // The unique name of this search definition.
    private String name;

    // True if this doesn't define a search, just some documents.
    private boolean documentsOnly = false;

    // The stemming setting of this search definition. Default is SHORTEST.
    // TODO: Change to Stemming.BEST on Vespa 7
    private Stemming stemming = Stemming.SHORTEST;

    // Documents contained in this definition.
    private SDDocumentType docType;

    // The extra fields of this search definition.
    private Map<String, SDField> fields = new LinkedHashMap<>();

    // The explicitly defined indices of this search definition.
    private Map<String, Index> indices = new LinkedHashMap<>();

    // The explicitly defined summaries of this search definition.
    private Map<String, DocumentSummary> summaries = new LinkedHashMap<>();
    // _Must_ preserve order

    // Ranking constants defined inside this s.d.
    private Map<String, RankingConstant> rankingConstants = new HashMap<>();

    private Optional<TemporaryImportedFields> temporaryImportedFields = Optional.of(new TemporaryImportedFields());
    private Optional<ImportedFields> importedFields = Optional.empty();

    private ApplicationPackage sourceApplication;

    /**
     * Creates a search definition which just holds a set of documents which should not (here, directly) be searchable
     */
    protected Search() {
        documentsOnly = true;
    }

    /**
     * Creates a proper search definition
     * @param name of the the searchdefinition
     * @param sourceApplication the application containing this
     */
    public Search(String name, ApplicationPackage sourceApplication) {
        this.sourceApplication = sourceApplication;
        this.name = name;
    }

    protected void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    /**
     * Returns true if this doesn't define a search, just some documents
     *
     * @return if the searchdefinition only has documents
     */
    public boolean isDocumentsOnly() {
        return documentsOnly;
    }

    /**
     * Sets the stemming default of fields. Default is ALL
     *
     * @param stemming set default stemming for this searchdefinition
     * @throws NullPointerException if this is attempted set to null
     */
    public void setStemming(Stemming stemming) {
        if (stemming == null) {
            throw new NullPointerException("The stemming setting of a search definition " +
                                           "can not be null");
        }
        this.stemming = stemming;
    }

    /**
     * Returns whether fields should be stemmed by default or not. Default is ALL. This is never null.
     *
     * @return the default stemming for this searchdefinition
     */
    public Stemming getStemming() {
        return stemming;
    }

    /**
     * Adds a document type which is defined in this search definition
     *
     * @param document the document type to add
     */
    public void addDocument(SDDocumentType document) {
        if (docType != null) {
            throw new IllegalArgumentException("Searchdefinition cannot have more than one document");
        }
        docType = document;
    }

    public void addRankingConstant(RankingConstant constant) {
        constant.validate();
        String name = constant.getName();
        if (rankingConstants.containsKey(name))
            throw new IllegalArgumentException("Ranking constant '" + name + "' defined twice");
        rankingConstants.put(name, constant);
    }

    /** Returns a read-only map of the ranking constants in this indexed by name */
    public Map<String, RankingConstant> getRankingConstants() {
        return Collections.unmodifiableMap(rankingConstants);
    }

    public Optional<TemporaryImportedFields> temporaryImportedFields() {
        return temporaryImportedFields;
    }

    public Optional<ImportedFields> importedFields() {
        return importedFields;
    }

    public void setImportedFields(ImportedFields importedFields) {
        temporaryImportedFields = Optional.empty();
        this.importedFields = Optional.of(importedFields);
    }

    @Override
    public Stream<ImmutableSDField> allImportedFields() {
        return importedFields
                .map(fields -> fields.fields().values().stream())
                .orElse(Stream.empty())
                .map(ImmutableImportedSDField::new);
    }

    @Override
    public ImmutableSDField getField(String name) {
        ImmutableSDField field = getConcreteField(name);
        if (field != null) {
            return field;
        }
        return allImportedFields()
                .filter(f -> f.getName().equals(name))
                .findFirst()
                .orElse(null);
    }

    @Override
    public Stream<ImmutableSDField> allFields() {
        Stream<ImmutableSDField> extraFields = extraFieldList().stream().map(ImmutableSDField.class::cast);
        Stream<ImmutableSDField> documentFields = docType.fieldSet().stream().map(ImmutableSDField.class::cast);
        return Stream.concat(
                extraFields,
                Stream.concat(documentFields, allImportedFields()));
    }

    /**
     * Gets a document from this search definition
     *
     * @param name the name of the document to return
     * @return the contained or used document type, or null if there is no such document
     */
    public SDDocumentType getDocument(String name) {
        if (docType != null && name.equals(docType.getName())) {
            return docType;
        }
        return null;
    }

    /**
     * @return true if the document has been added.
     */
    public boolean hasDocument() {
        return docType != null;
    }

    /**
     * @return The document in this search.
     */
    public SDDocumentType getDocument() {
        return docType;
    }

    /**
     * Returns a list of all the fields of this search definition, that is all fields in all documents, in the documents
     * they inherit, and all extra fields. The caller receives ownership to the list - subsequent changes to it will not
     * impact this Search
     *
     * @return the list of fields in this searchdefinition
     */
    public List<SDField> allConcreteFields() {
        List<SDField> allFields = new ArrayList<>();
        allFields.addAll(extraFieldList());
        for (Field field : docType.fieldSet()) {
            allFields.add((SDField)field);
        }
        return allFields;
    }

    /**
     * Returns the content of a ranking expression file
     */
    public Reader getRankingExpression(String fileName) {
        return sourceApplication.getRankingExpression(fileName);
    }

    public ApplicationPackage sourceApplication() { return sourceApplication; }

    /**
     * Returns a field defined in this search definition or one if its documents. Fields in this search definition takes
     * precedence over document fields having the same name
     *
     * @param name of the field
     * @return the SDField representing the field
     */
    public SDField getConcreteField(String name) {
        SDField field = getExtraField(name);
        if (field != null) {
            return field;
        }
        return (SDField)docType.getField(name);
    }

    /**
     * Returns a field defined in one of the documents of this search definition. This does <b>not</b> include the extra
     * fields defined outside of a document (those accessible through the getExtraField() method).
     *
     * @param name The name of the field to return.
     * @return The named field, or null if not found.
     */
    public SDField getDocumentField(String name) {
        return (SDField)docType.getField(name);
    }

    /**
     * Adds an extra field of this search definition not contained in a document
     *
     * @param field to add to the searchdefinitions list of external fields.
     */
    public void addExtraField(SDField field) {
        if (fields.containsKey(field.getName())) {
            log.warning("Duplicate field " + field.getName() + " in search definition " + getName());
        } else {
            field.setIsExtraField(true);
            fields.put(field.getName(), field);
        }
    }

    public Collection<SDField> extraFieldList() {
        return fields.values();
    }
    public Collection<SDField> allExtraFields() {
        Map<String, SDField> extraFields = new TreeMap<>();
        for (Field field : docType.fieldSet()) {
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
        return fields.get(fieldName);
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
     * <p>Returns an index, or null if no index with this name has had some <b>explicit settings</b> applied. Even if
     * this returns null, the index may be implicitly defined by an indexing statement.</p>
     * <p>This will return the
     * index whether it is defined on this search or on one of its fields</p>
     *
     * @param name the name of the index to get
     * @return the index requested
     */
    public Index getIndex(String name) {
        List<Index> sameIndices = new ArrayList<>(1);
        Index searchIndex = indices.get(name);
        if (searchIndex != null) {
            sameIndices.add(searchIndex);
        }

        for (SDField field : allConcreteFields()) {
            Index index = field.getIndex(name);
            if (index != null) {
                sameIndices.add(index);
            }
        }
        if (sameIndices.size() == 0) {
            return null;
        }
        if (sameIndices.size() == 1) {
            return sameIndices.get(0);
        }
        return consolidateIndices(sameIndices);
    }

    public boolean existsIndex(String name) {
        if (indices.get(name) != null) {
            return true;
        }
        for (SDField field : allConcreteFields()) {
            if (field.existsIndex(name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Consolidates a set of index settings for the same index into one
     *
     * @param indices The list of indexes to consolidate.
     * @return The consolidated index
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

            if (consolidated.getRankType() == null) {
                consolidated.setRankType(current.getRankType());
            } else {
                if (current.getRankType() != null &&
                    !consolidated.getRankType().equals(current.getRankType()))
                {
                    log.warning("Conflicting rank type settings for " +
                                first.getName() + " in " + this + ", using " +
                                consolidated.getRankType());
                }
            }

            for (Iterator<String> j = current.aliasIterator(); j.hasNext();) {
                consolidated.addAlias(j.next());
            }
        }
        return consolidated;
    }

    /**
     * All explicitly defined indices, both on this search definition itself (returned first) and all its fields
     *
     * @return The list of explicit defined indexes.
     */
    public List<Index> getExplicitIndices() {
        List<Index> allIndices = new ArrayList<>(indices.values());
        for (SDField field : allConcreteFields()) {
            for (Index index : field.getIndices().values()) {
                allIndices.add(index);
            }
        }
        return Collections.unmodifiableList(allIndices);
    }

    /**
     * Adds an explicitly defined summary to this search definition
     *
     * @param summary The summary to add.
     */
    public void addSummary(DocumentSummary summary) {
        summaries.put(summary.getName(), summary);
    }

    /**
     * <p>Returns a summary class defined by this search definition, or null if no summary with this name is defined.
     * The default summary, named "default" is always present.</p>
     *
     * @param name the name of the summary to get.
     * @return Summary found.
     */
    public DocumentSummary getSummary(String name) {
        return summaries.get(name);
    }

    /**
     * Returns the first explicit instance found of a summary field with this name, or null if not present (implicitly
     * or explicitly) in any summary class.
     *
     * @param name The name of the summaryfield to get.
     * @return SummaryField to return.
     */
    public SummaryField getSummaryField(String name) {
        for (DocumentSummary summary : summaries.values()) {
            SummaryField summaryField = summary.getSummaryField(name);
            if (summaryField != null) {
                return summaryField;
            }
        }
        return null;
    }

    /**
     * Returns the first explicit instance found of a summary field with this name, or null if not present explicitly in
     * any summary class
     *
     * @param name Thge name of the explicit summary field to get.
     * @return The SummaryField found.
     */
    public SummaryField getExplicitSummaryField(String name) {
        for (DocumentSummary summary : summaries.values()) {
            SummaryField summaryField = summary.getSummaryField(name);
            if (summaryField != null && !summaryField.isImplicit()) {
                return summaryField;
            }
        }
        return null;
    }

    /**
     * Summaries defined by fields of this search definition. The default summary, named "default", is always the first
     * one in the returned iterator.
     *
     * @return The map of document summaries.
     */
    public Map<String, DocumentSummary> getSummaries() {
        return summaries;
    }

    /**
     * <p>Returns all summary fields, of all document summaries, which has the given field as source. If there are
     * multiple summary fields with the same name, the last one will be used (they should all have the same content, if
     * this is a valid search definition).</p> <p>The map gets owned by the receiver.</p>
     *
     * @param field The source field.
     * @return The map of summary fields found.
     */
    @Override
    public Map<String, SummaryField> getSummaryFields(ImmutableSDField field) {
        Map<String, SummaryField> summaryFields = new java.util.LinkedHashMap<>();
        for (DocumentSummary documentSummary : summaries.values()) {
            for (SummaryField summaryField : documentSummary.getSummaryFields()) {
                if (summaryField.hasSource(field.getName())) {
                    summaryFields.put(summaryField.getName(), summaryField);
                }
            }
        }
        return summaryFields;
    }

    /**
     * <p>Returns one summary field for each summary field name. If there are multiple summary fields with the same
     * name, the last one will be used. Multiple fields of the same name should all have the same content in a valid
     * search definition, except from the destination set. So this method can be used for all summary handling except
     * processing the destination set.</p> <p>The map gets owned by the receiver.</p>
     *
     * @return Map of unique summary fields
     */
    public Map<String, SummaryField> getUniqueNamedSummaryFields() {
        Map<String, SummaryField> summaryFields = new java.util.LinkedHashMap<>();
        for (DocumentSummary documentSummary : summaries.values()) {
            for (SummaryField summaryField : documentSummary.getSummaryFields()) {
                summaryFields.put(summaryField.getName(), summaryField);
            }
        }
        return summaryFields;
    }

    public int hashCode() {
        return name.hashCode();
    }

    /**
     * Returns the first occurence of an attribute having this name, or null if none
     *
     * @param name Name of attribute
     * @return The Attribute with given name.
     */
    public Attribute getAttribute(String name) {
        for (SDField field : allConcreteFields()) {
            Attribute attribute = field.getAttributes().get(name);
            if (attribute != null) {
                return attribute;
            }
        }
        return null;
    }

    public boolean equals(Object o) {
        if (!(o instanceof Search)) {
            return false;
        }

        Search other = (Search)o;
        return getName().equals(other.getName());
    }

    public String toString() {
        return "search definition '" + getName() + "'";
    }

    public boolean isAccessingDiskSummary(SummaryField field) {
        if (!field.getTransform().isInMemory()) {
            return true;
        }
        if (field.getSources().size() == 0) {
            return isAccessingDiskSummary(getName());
        }
        for (SummaryField.Source source : field.getSources()) {
            if (isAccessingDiskSummary(source.getName())) {
                return true;
            }
        }
        return false;
    }

    private boolean isAccessingDiskSummary(String source) {
        SDField field = getConcreteField(source);
        if (field == null) {
            return false;
        }
        if (field.doesSummarying() && !field.doesAttributing()) {
            return true;
        }
        return false;
    }

    public void process() {
        if (processed) {
            throw new IllegalStateException("Search '" + getName() + "' already processed.");
        }
        processed = true;
    }

    public boolean isProcessed() {
        return processed;
    }

    /**
     * The field set settings for this search
     *
     * @return field set settings for this
     */
    public FieldSets fieldSets() {
        return fieldSets;
    }

    /**
     * For adding structs defined in document scope
     *
     * @param dt The struct to add.
     * @return self, for chaining
     */
    public Search addType(SDDocumentType dt) {
        docType.addType(dt); // TODO This is a very very dirty thing. It must go
        return this;
    }

    public Search addAnnotation(SDAnnotationType dt) {
        docType.addAnnotation(dt);
        return this;
    }
}
