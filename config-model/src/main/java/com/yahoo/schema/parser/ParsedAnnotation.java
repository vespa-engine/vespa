// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.parser;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * This class holds the extracted information after parsing a
 * "annotation" block, using simple data structures as far as
 * possible.  Do not put advanced logic here!
 * @author arnej27959
 **/
public class ParsedAnnotation extends ParsedBlock {

    private ParsedStruct wrappedStruct = null;
    private final List<String> inherited = new ArrayList<>();
    private final List<ParsedAnnotation> resolvedInherits = new ArrayList<>();
    private ParsedDocument ownedBy = null;

    public ParsedAnnotation(String name) {
        super(name, "annotation");
    }

    public List<String> getInherited() { return List.copyOf(inherited); }
    public List<ParsedAnnotation> getResolvedInherits() {
        assert(inherited.size() == resolvedInherits.size());
        return List.copyOf(resolvedInherits);
    }


    public Optional<ParsedStruct> getStruct() { return Optional.ofNullable(wrappedStruct); }
    public ParsedDocument getOwnerDoc() { return ownedBy; }
    public String getOwnerName() { return ownedBy.name(); }

    public ParsedStruct ensureStruct() {
        if (wrappedStruct == null) {
            wrappedStruct = new ParsedStruct("annotation." + name());
            wrappedStruct.tagOwner(ownedBy);
        }
        return wrappedStruct;
    }
    public void setStruct(ParsedStruct struct) { this.wrappedStruct = struct; }

    public void inherit(String other) { inherited.add(other); }

    void tagOwner(ParsedDocument owner) {
        verifyThat(ownedBy == null, "already owned by", ownedBy);
        this.ownedBy = owner;
        getStruct().ifPresent(s -> s.tagOwner(owner));
    }

    void resolveInherit(String name, ParsedAnnotation parsed) {
        verifyThat(inherited.contains(name), "resolveInherit for non-inherited name", name);
        verifyThat(name.equals(parsed.name()), "resolveInherit name mismatch for", name);
        resolvedInherits.add(parsed);
    }
}
