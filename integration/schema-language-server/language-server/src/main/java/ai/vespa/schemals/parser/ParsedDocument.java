package ai.vespa.schemals.parser;

import java.util.ArrayList;

class ParsedDocument extends ParsedBlock {
    private ArrayList<ParsedField> fields = new ArrayList<ParsedField>();

    ParsedDocument(String name) {
        super(name, "document");
    }

    void addField(ParsedField field) {
        fields.add(field);
    }
}