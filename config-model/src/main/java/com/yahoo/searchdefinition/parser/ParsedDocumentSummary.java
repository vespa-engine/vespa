
package com.yahoo.searchdefinition.parser;

import java.util.ArrayList;
import java.util.List;

/**
 * This class holds the extracted information after parsing a
 * "document-summary" block, using simple data structures as far as
 * possible.  Do not put advanced logic here!
 * @author arnej27959
 **/
class ParsedDocumentSummary {

    public final String name;
    final List<ParsedSummaryField> fields = new ArrayList<>();

    ParsedDocumentSummary(String name) {
        this.name = name;
    }

    void addField(ParsedSummaryField field) { fields.add(field); }
    void setFromDisk(boolean value) {}
    void setOmitSummaryFeatures(boolean value) {}
    void inherit(String other) {}
}
