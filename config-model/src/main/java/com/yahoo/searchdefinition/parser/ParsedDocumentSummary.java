
package com.yahoo.searchdefinition.parser;

import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * This class holds the extracted information after parsing a
 * "document-summary" block, using simple data structures as far as
 * possible.  Do not put advanced logic here!
 * @author arnej27959
 **/
class ParsedDocumentSummary {

    private final String name;
    private boolean omitSummaryFeatures;
    private boolean fromDisk;
    private final List<String> inherited = new ArrayList<>();
    private final Map<String, ParsedSummaryField> fields = new HashMap<>();

    ParsedDocumentSummary(String name) {
        this.name = name;
    }

    String name() { return this.name; }
    boolean getOmitSummaryFeatures() { return omitSummaryFeatures; }
    boolean getFromDisk() { return fromDisk; }
    List<ParsedSummaryField> getSummaryFields() { return ImmutableList.copyOf(fields.values()); }
    List<String> getInherited() { return ImmutableList.copyOf(inherited); }

    void addField(ParsedSummaryField field) {
        String fieldName = field.name();
        if (fields.containsKey(fieldName)) {
            throw new IllegalArgumentException("document-summary "+this.name+" already has field "+fieldName);
        }
        fields.put(fieldName, field);
    }

    void setFromDisk(boolean value) {
        this.fromDisk = value;
    }

    void setOmitSummaryFeatures(boolean value) {
        this.omitSummaryFeatures = value;
    }

    void inherit(String other) {
        inherited.add(other);
    }
}
