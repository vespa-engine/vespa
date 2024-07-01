package ai.vespa.schemals.parser;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

class ParsedDocument extends ParsedBlock {
    private ArrayList<ParsedField> fields = new ArrayList<ParsedField>();
    private final Map<String, ParsedStruct> docStructs = new LinkedHashMap<>();

    ParsedDocument(String name) {
        super(name, "document");
    }

    void addField(ParsedField field) {
        fields.add(field);
    }

    void addStruct(ParsedStruct struct) {
        String sName = struct.name();
        verifyThat(! docStructs.containsKey(sName), "already has struct", sName);
        docStructs.put(sName, struct);
        struct.tagOwner(this);
    }
}