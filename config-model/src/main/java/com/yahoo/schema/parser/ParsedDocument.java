// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.parser;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * This class holds the extracted information after parsing a
 * "document" block in a schema (.sd) file, using simple data
 * structures as far as possible.  Do not put advanced logic here!
 * @author arnej27959
 **/
public class ParsedDocument extends ParsedBlock {
    private final List<String> inherited = new ArrayList<>();
    private final Map<String, ParsedDocument> resolvedInherits = new LinkedHashMap();
    private final Map<String, ParsedDocument> resolvedReferences = new LinkedHashMap();
    private final Map<String, ParsedField> docFields = new LinkedHashMap<>();
    private final Map<String, ParsedStruct> docStructs = new LinkedHashMap<>();
    private final Map<String, ParsedAnnotation> docAnnotations = new LinkedHashMap<>();

    public ParsedDocument(String name) {
        super(name, "document");
    }

    List<String> getInherited() { return List.copyOf(inherited); }
    List<ParsedAnnotation> getAnnotations() { return List.copyOf(docAnnotations.values()); }
    List<ParsedDocument> getResolvedInherits() {
        assert(inherited.size() == resolvedInherits.size());
        return List.copyOf(resolvedInherits.values());
    }
    List<ParsedDocument> getResolvedReferences() {
        return List.copyOf(resolvedReferences.values());
    }
    List<ParsedDocument> getAllResolvedParents() {
        List<ParsedDocument> all = new ArrayList<>();
        all.addAll(getResolvedInherits());
        all.addAll(getResolvedReferences());
        return all;
    }
    List<ParsedField> getFields() { return List.copyOf(docFields.values()); }
    List<ParsedStruct> getStructs() { return List.copyOf(docStructs.values()); }
    ParsedStruct getStruct(String name) { return docStructs.get(name); }
    ParsedAnnotation getAnnotation(String name) { return docAnnotations.get(name); }

    List<String> getReferencedDocuments() {
        var result = new ArrayList<String>();
        for (var field : docFields.values()) {
            var type = field.getType();
            if (type.getVariant() == ParsedType.Variant.DOC_REFERENCE) {
                var docType = type.getReferencedDocumentType();
                assert(docType.getVariant() == ParsedType.Variant.DOCUMENT);
                result.add(docType.name());
            }
        }
        return result;
    }

    void inherit(String other) { inherited.add(other); }

    void addField(ParsedField field) {
        String fieldName = field.name().toLowerCase();
        verifyThat(! docFields.containsKey(fieldName),
                   "Duplicate (case insensitively) " + field + " in document type '" + this.name() + "'");
        docFields.put(fieldName, field);
    }

    void addStruct(ParsedStruct struct) {
        String sName = struct.name();
        verifyThat(! docStructs.containsKey(sName), "already has struct", sName);
        docStructs.put(sName, struct);
        struct.tagOwner(this);
    }

    void addAnnotation(ParsedAnnotation annotation) {
        String annName = annotation.name();
        verifyThat(! docAnnotations.containsKey(annName), "already has annotation", annName);
        docAnnotations.put(annName, annotation);
        annotation.tagOwner(this);
    }

    void resolveInherit(String name, ParsedDocument parsed) {
        verifyThat(inherited.contains(name), "resolveInherit for non-inherited name", name);
        verifyThat(name.equals(parsed.name()), "resolveInherit name mismatch for", name);
        verifyThat(! resolvedInherits.containsKey(name), "double resolveInherit for", name);
        resolvedInherits.put(name, parsed);
    }

    void resolveReferenced(ParsedDocument parsed) {
        var old = resolvedReferences.put(parsed.name(), parsed);
        assert(old == null || old == parsed);
    }

    ParsedStruct findParsedStruct(String name) {
        ParsedStruct found = getStruct(name);
        if (found != null) return found;
        for (var parent : getAllResolvedParents()) {
            var fromParent = parent.findParsedStruct(name);
            if (fromParent == null) continue;
            if (fromParent == found) continue;
            if (found == null) {
                found = fromParent;
            } else {
                throw new IllegalArgumentException("conflicting values for struct " + name + " in " +this);
            }
        }
        return found;
    }

    ParsedAnnotation findParsedAnnotation(String name) {
        ParsedAnnotation found = docAnnotations.get(name);
        if (found != null) return found;
        for (var parent : getResolvedInherits()) {
            var fromParent = parent.findParsedAnnotation(name);
            if (fromParent == null) continue;
            if (fromParent == found) continue;
            if (found == null) {
                found = fromParent;
            } else {
                throw new IllegalArgumentException("conflicting values for annotation " + name + " in " +this);
            }
        }
        return found;
    }

}
