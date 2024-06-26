package ai.vespa.schemals.parser;

import java.util.ArrayList;

public class ParsedSchema {
    private String name;
    private ArrayList<ParsedDocument> documents;

    public ParsedSchema(String name) {
        this.name = name;
        documents = new ArrayList<ParsedDocument>();
    }


    public String toString() {
        String ret = "ParsedSchema(" + name + ")";

        for (ParsedDocument document : documents) {
            ret += "\n\t" + document.toString().replaceAll("\n", "\n\t");
        }
        return ret;
    }

    public String getName() {
        return name;
    }

    public void addDocument(ParsedDocument document) {
        documents.add(document);
    }
}
