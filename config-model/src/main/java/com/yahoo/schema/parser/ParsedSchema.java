// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.parser;

import com.yahoo.schema.OnnxModel;
import com.yahoo.schema.RankProfile;
import com.yahoo.schema.document.Stemming;
import com.yahoo.searchlib.rankingexpression.Reference;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * This class holds the extracted information after parsing
 * one schema (.sd) file, using simple data structures
 * as far as possible.
 *
 * Do not put complicated logic here!
 *
 * @author arnej27959
 */
public class ParsedSchema extends ParsedBlock {

    public static class ImportedField {
        public final String asFieldName;
        public final String refFieldName;
        public final String foreignFieldName;
        public ImportedField(String asField, String refField, String foreignField) {
            this.asFieldName = asField;
            this.refFieldName = refField;
            this.foreignFieldName = foreignField;
        }
    }

    private boolean documentWithoutSchema = false;
    private Boolean rawAsBase64 = null;
    private ParsedDocument myDocument = null;
    private Stemming defaultStemming = null;
    private final List<ImportedField> importedFields = new ArrayList<>();
    private final List<OnnxModel> onnxModels = new ArrayList<>();
    private final Map<Reference, RankProfile.Constant> constants = new LinkedHashMap<>();
    private final List<String> inherited = new ArrayList<>();
    private final List<String> inheritedByDocument = new ArrayList<>();
    private final Map<String, ParsedSchema> resolvedInherits = new LinkedHashMap<>();
    private final Map<String, ParsedSchema> allResolvedInherits = new LinkedHashMap<>();
    private final Map<String, ParsedAnnotation> extraAnnotations = new LinkedHashMap<>();
    private final Map<String, ParsedDocumentSummary> docSums = new LinkedHashMap<>();
    private final Map<String, ParsedField> extraFields = new LinkedHashMap<>();
    private final Map<String, ParsedFieldSet> fieldSets = new LinkedHashMap<>();
    private final Map<String, ParsedIndex> extraIndexes = new LinkedHashMap<>();
    private final Map<String, ParsedRankProfile> rankProfiles = new LinkedHashMap<>();
    private final Map<String, ParsedStruct> extraStructs = new LinkedHashMap<>();

    public ParsedSchema(String name) {
        super(name, "schema");
    }

    boolean getDocumentWithoutSchema() { return documentWithoutSchema; }
    Optional<Boolean> getRawAsBase64() { return Optional.ofNullable(rawAsBase64); }
    boolean hasDocument() { return myDocument != null; }
    ParsedDocument getDocument() { return myDocument; }
    boolean hasStemming() { return defaultStemming != null; }
    Stemming getStemming() { return defaultStemming; }
    List<ImportedField> getImportedFields() { return List.copyOf(importedFields); }
    List<OnnxModel> getOnnxModels() { return List.copyOf(onnxModels); }
    List<ParsedAnnotation> getAnnotations() { return List.copyOf(extraAnnotations.values()); }
    List<ParsedDocumentSummary> getDocumentSummaries() { return List.copyOf(docSums.values()); }
    List<ParsedField> getFields() { return List.copyOf(extraFields.values()); }
    List<ParsedFieldSet> getFieldSets() { return List.copyOf(fieldSets.values()); }
    List<ParsedIndex> getIndexes() { return List.copyOf(extraIndexes.values()); }
    List<ParsedStruct> getStructs() { return List.copyOf(extraStructs.values()); }
    List<String> getInherited() { return List.copyOf(inherited); }
    List<String> getInheritedByDocument() { return List.copyOf(inheritedByDocument); }
    List<ParsedRankProfile> getRankProfiles() { return List.copyOf(rankProfiles.values()); }
    List<ParsedSchema> getResolvedInherits() { return List.copyOf(resolvedInherits.values()); }
    List<ParsedSchema> getAllResolvedInherits() { return List.copyOf(allResolvedInherits.values()); }
    List<RankProfile.Constant> getConstants() { return List.copyOf(constants.values()); }

    void addAnnotation(ParsedAnnotation annotation) {
        String annName = annotation.name();
        verifyThat(! extraAnnotations.containsKey(annName), "already has annotation", annName);
        extraAnnotations.put(annName, annotation);
    }

    void addDocument(ParsedDocument document) {
        verifyThat(myDocument == null,
                   "already has", myDocument, "so cannot add", document);
        // TODO - disallow?
        // verifyThat(name().equals(document.name()),
        // "schema " + name() + " can only contain document named " + name() + ", was: "+ document.name());
        this.myDocument = document;
    }

    void setDocumentWithoutSchema() { this.documentWithoutSchema = true; }

    void addDocumentSummary(ParsedDocumentSummary docsum) {
        String dsName = docsum.name();
        verifyThat(! docSums.containsKey(dsName), "already has document-summary", dsName);
        docSums.put(dsName, docsum);
    }

    void addField(ParsedField field) {
        String fieldName = field.name();
        verifyThat(! extraFields.containsKey(fieldName), "already has field", fieldName);
        extraFields.put(fieldName, field);
    }

    void addFieldSet(ParsedFieldSet fieldSet) {
        String fsName = fieldSet.name();
        verifyThat(! fieldSets.containsKey(fsName), "already has fieldset", fsName);
        fieldSets.put(fsName, fieldSet);
    }

    void addImportedField(String asFieldName, String refFieldName, String foregnFieldName) {
        importedFields.add(new ImportedField(asFieldName, refFieldName, foregnFieldName));
    }

    void addIndex(ParsedIndex index) {
        String idxName = index.name();
        verifyThat(! extraIndexes.containsKey(idxName), "already has index", idxName);
        extraIndexes.put(idxName, index);
    }

    void add(OnnxModel model) {
        onnxModels.add(model);
    }

    void addRankProfile(ParsedRankProfile profile) {
        String rpName = profile.name();
        verifyThat(! rankProfiles.containsKey(rpName), "already has rank-profile", rpName);
        rankProfiles.put(rpName, profile);
    }

    void add(RankProfile.Constant constant) {
        constants.put(constant.name(), constant);
    }

    void addStruct(ParsedStruct struct) {
        String sName = struct.name();
        verifyThat(! extraStructs.containsKey(sName), "already has struct", sName);
        extraStructs.put(sName, struct);
    }

    void enableRawAsBase64(boolean value) {
        this.rawAsBase64 = value;
    }

    void inherit(String other) { inherited.add(other); }

    void inheritByDocument(String other) { inheritedByDocument.add(other); }

    void setStemming(Stemming value) {
        verifyThat((defaultStemming == null) || (defaultStemming == value),
                   "already has stemming", defaultStemming, "cannot also set", value);
        defaultStemming = value;
    }

    void resolveInherit(String name, ParsedSchema parsed) {
        verifyThat(inherited.contains(name), "resolveInherit for non-inherited name", name);
        verifyThat(name.equals(parsed.name()), "resolveInherit name mismatch for", name);
        verifyThat(! resolvedInherits.containsKey(name), "double resolveInherit for", name);
        resolvedInherits.put(name, parsed);
        var old = allResolvedInherits.put("schema " + name, parsed);
        verifyThat(old == null || old == parsed, "conflicting resolveInherit for", name);
    }

    void resolveInheritByDocument(String name, ParsedSchema parsed) {
        verifyThat(inheritedByDocument.contains(name),
                   "resolveInheritByDocument for non-inherited name", name);
        var old = allResolvedInherits.put("document " + name, parsed);
        verifyThat(old == null || old == parsed, "conflicting resolveInheritByDocument for", name);
    }

}
