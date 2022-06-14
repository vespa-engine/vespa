
package com.yahoo.schema.parser;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * This class holds the extracted information after parsing a
 * "document-summary" block, using simple data structures as far as
 * possible.  Do not put advanced logic here!
 * @author arnej27959
 **/
class ParsedDocumentSummary extends ParsedBlock {

    private boolean omitSummaryFeatures;
    private boolean fromDisk;
    private final List<String> inherited = new ArrayList<>();
    private final Map<String, ParsedSummaryField> fields = new LinkedHashMap<>();

    ParsedDocumentSummary(String name) {
        super(name, "document-summary");
    }

    boolean getOmitSummaryFeatures() { return omitSummaryFeatures; }
    boolean getFromDisk() { return fromDisk; }
    List<ParsedSummaryField> getSummaryFields() { return List.copyOf(fields.values()); }
    List<String> getInherited() { return List.copyOf(inherited); }

    ParsedSummaryField addField(ParsedSummaryField field) {
        String fieldName = field.name();
        verifyThat(! fields.containsKey(fieldName), "already has field", fieldName);
        return fields.put(fieldName, field);
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
