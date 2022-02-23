// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.parser;


import com.yahoo.searchdefinition.OnnxModel;
import com.yahoo.searchdefinition.RankingConstant;
import com.yahoo.searchdefinition.document.Stemming;

import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * This class holds the extracted information after parsing
 * one schema (.sd) file, using simple data structures
 * as far as possible.  Do not put advanced logic here!
 * @author arnej27959
 **/
public class ParsedSchema {

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

    private final String name;
    private boolean rawAsBase64 = false; // TODO Vespa 8 flip default
    private Optional<ParsedDocument> myDocument = Optional.empty();
    private Optional<Stemming> defaultStemming = Optional.empty();
    private final List<ImportedField> importedFields = new ArrayList<>();
    private final List<OnnxModel> onnxModels = new ArrayList<>();
    private final List<RankingConstant> rankingConstants = new ArrayList<>();
    private final List<String> inherited = new ArrayList<>();
    private final Map<String, ParsedAnnotation> extraAnnotations = new HashMap<>();
    private final Map<String, ParsedDocumentSummary> docSums = new HashMap<>();
    private final Map<String, ParsedField> extraFields = new HashMap<>();
    private final Map<String, ParsedFieldSet> fieldSets = new HashMap<>();
    private final Map<String, ParsedIndex> extraIndexes = new HashMap<>();
    private final Map<String, ParsedRankProfile> rankProfiles = new HashMap<>();
    private final Map<String, ParsedStruct> extraStructs = new HashMap<>();

    public  ParsedSchema(String name) {
        this.name = name;
    }

    String name() { return name; }
    boolean getRawAsBase64() { return rawAsBase64; }
    boolean hasDocument() { return myDocument.isPresent(); }
    ParsedDocument getDocument() { return myDocument.get(); }
    boolean hasStemming() { return defaultStemming.isPresent(); }
    Stemming getStemming() { return defaultStemming.get(); }
    List<ImportedField> getImportedFields() { return ImmutableList.copyOf(importedFields); }
    List<OnnxModel> getOnnxModels() { return ImmutableList.copyOf(onnxModels); }
    List<ParsedAnnotation> getAnnotations() { return ImmutableList.copyOf(extraAnnotations.values()); }
    List<ParsedDocumentSummary> getDocumentSummaries() { return ImmutableList.copyOf(docSums.values()); }
    List<ParsedField> getFields() { return ImmutableList.copyOf(extraFields.values()); }
    List<ParsedFieldSet> getFieldSets() { return ImmutableList.copyOf(fieldSets.values()); }
    List<ParsedIndex> getIndexes() { return ImmutableList.copyOf(extraIndexes.values()); }
    List<ParsedRankProfile> getRankProfiles() { return ImmutableList.copyOf(rankProfiles.values()); }
    List<ParsedStruct> getStructs() { return ImmutableList.copyOf(extraStructs.values()); }
    List<RankingConstant> getRankingConstants() { return ImmutableList.copyOf(rankingConstants); }
    List<String> getInherited() { return ImmutableList.copyOf(inherited); }

    void addAnnotation(ParsedAnnotation annotation) {
        String annName = annotation.name();
        if (extraAnnotations.containsKey(annName)) {
            throw new IllegalArgumentException("schema "+this.name+" already has annotation "+annName);
        }
        extraAnnotations.put(annName, annotation);
    }

    void addDocument(ParsedDocument document) {
        if (myDocument.isPresent()) {
            throw new IllegalArgumentException("schema "+this.name+" already has "+myDocument.get().name()
                                               + "cannot add document "+document.name());
        }
        myDocument = Optional.of(document);
    }

    void addDocumentSummary(ParsedDocumentSummary docsum) {
        String dsName = docsum.name();
        if (docSums.containsKey(dsName)) {
            throw new IllegalArgumentException("schema "+this.name+" already has document-summary "+dsName);
        }
        docSums.put(dsName, docsum);
    }

    void addField(ParsedField field) {
        String fieldName = field.name();
        if (extraFields.containsKey(fieldName)) {
            throw new IllegalArgumentException("schema "+this.name+" already has field "+fieldName);
        }
        extraFields.put(fieldName, field);
    }

    void addFieldSet(ParsedFieldSet fieldSet) {
        String fsName = fieldSet.name();
        if (fieldSets.containsKey(fsName)) {
            throw new IllegalArgumentException("schema "+this.name+" already has fieldset "+fsName);
        }
        fieldSets.put(fsName, fieldSet);
    }

    void addImportedField(String asFieldName, String refFieldName, String foregnFieldName) {
        importedFields.add(new ImportedField(asFieldName, refFieldName, foregnFieldName));
    }

    void addIndex(ParsedIndex index) {
        String idxName = index.name();
        if (extraIndexes.containsKey(idxName)) {
            throw new IllegalArgumentException("schema "+this.name+" already has index "+idxName);
        }
        extraIndexes.put(idxName, index);
    }

    void addOnnxModel(OnnxModel model) {
        onnxModels.add(model);
    }

    void addRankProfile(ParsedRankProfile profile) {
        String rpName = profile.name();
        if (rankProfiles.containsKey(rpName)) {
            throw new IllegalArgumentException("schema "+this.name+" already has rank-profile "+rpName);
        }
        rankProfiles.put(rpName, profile);
    }

    void addRankingConstant(RankingConstant constant) {
        rankingConstants.add(constant);
    }

    void addStruct(ParsedStruct struct) {
        String sName = struct.name();
        if (extraStructs.containsKey(sName)) {
            throw new IllegalArgumentException("schema "+this.name+" already has struct "+sName);
        }
        extraStructs.put(sName, struct);
    }

    void enableRawAsBase64(boolean value) {
        this.rawAsBase64 = value;
    }

    void inherit(String other) { inherited.add(other); }

    void setStemming(Stemming value) {
        if (defaultStemming.isPresent() && (defaultStemming.get() != value)) {
            throw new IllegalArgumentException("schema " + this.name + " already has stemming "
                                               + defaultStemming.get() + "cannot also set " + value);
        }
        defaultStemming = Optional.of(value);
    }
}
