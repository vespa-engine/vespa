// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.parser;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * This class holds the extracted information after parsing a "struct"
 * block, using simple data structures as far as possible.  Do not put
 * advanced logic here!
 * @author arnej27959
 **/
public class ParsedStruct extends ParsedBlock {
    private final List<String> inherited = new ArrayList<>();
    private final List<ParsedStruct> resolvedInherits = new ArrayList<>();
    private final Map<String, ParsedField> fields = new LinkedHashMap<>();
    private final ParsedType asParsedType;
    private ParsedDocument ownedBy = null;

    public ParsedStruct(String name) {
        super(name, "struct");
        this.asParsedType = ParsedType.fromName(name);
        asParsedType.setVariant(ParsedType.Variant.STRUCT);
    }

    List<ParsedField> getFields() { return List.copyOf(fields.values()); }
    List<String> getInherited() { return List.copyOf(inherited); }
    ParsedDocument getOwnerDoc() {
        verifyThat(ownedBy != null, "not owned by any document");
        return ownedBy;
    }
    String getOwnerName() { return getOwnerDoc().name(); }
    String getFullName() { return name() + " @ " + getOwnerName(); }

    List<ParsedStruct> getResolvedInherits() {
        assert(inherited.size() == resolvedInherits.size());
        return List.copyOf(resolvedInherits);
    }

    void addField(ParsedField field) {
        String fieldName = field.name();
        verifyThat(! fields.containsKey(fieldName), "already has field", fieldName);
        fields.put(fieldName, field);
    }

    void inherit(String other) {
        verifyThat(! name().equals(other), "cannot inherit from itself");
        inherited.add(other);
    }

    void tagOwner(ParsedDocument document) {
        verifyThat(ownedBy == null, "already owned by document "+ownedBy);
        this.ownedBy = document;
    }

    void resolveInherit(String name, ParsedStruct parsed) {
        verifyThat(inherited.contains(name), "resolveInherit for non-inherited name", name);
        verifyThat(name.equals(parsed.name()), "resolveInherit name mismatch for", name);
        resolvedInherits.add(parsed);
    }

}

