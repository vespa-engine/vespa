package ai.vespa.schemals.parser;

import java.util.ArrayList;

class ParsedDocument {
    private String name;
    private ArrayList<ParsedField> fields = new ArrayList<ParsedField>();

    ParsedDocument(String name) {
        this.name = name;
    }

    public String toString() {
        String ret = "ParsedDocument(" + name + ")";
        for (ParsedField field : fields) {
            ret += "\n\t" + field.toString().replaceAll("\n", "\n\t");
        }
        return ret;
    }

    void addField(ParsedField field) {
        fields.add(field);
    }
}