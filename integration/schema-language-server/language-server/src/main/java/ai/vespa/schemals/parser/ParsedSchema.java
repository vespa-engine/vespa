package ai.vespa.schemals.parser;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

public class ParsedSchema extends ParsedBlock {
    private ArrayList<ParsedDocument> documents = new ArrayList<ParsedDocument>();
    private final Map<String, ParsedFieldSet> fieldSets = new LinkedHashMap<>();

    public ParsedSchema(String name) {
        super(name, "schema");
    }


    public String toString() {
        String ret = "ParsedSchema(" + name() + ")";

        for (ParsedDocument document : documents) {
            ret += "\n\t" + document.toString().replaceAll("\n", "\n\t");
        }
        return ret;
    }

    public void addDocument(ParsedDocument document) {
        documents.add(document);
        // TODO: Refactor. Seems like only one document is allowed in a schema
    }

    void addFieldSet(ParsedFieldSet fieldSet) {
        String fsName = fieldSet.name();
        verifyThat(! fieldSets.containsKey(fsName), "already has fieldset", fsName);
        fieldSets.put(fsName, fieldSet);
    }
}
