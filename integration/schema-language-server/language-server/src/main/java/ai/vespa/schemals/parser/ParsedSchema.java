package ai.vespa.schemals.parser;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

public class ParsedSchema extends ParsedBlock {
    private ParsedDocument myDocument = null;
    private final Map<String, ParsedFieldSet> fieldSets = new LinkedHashMap<>();

    public ParsedSchema(String name) {
        super(name, "schema");
    }

    public void addDocument(ParsedDocument document) {
        verifyThat(myDocument == null,
                   "already has", myDocument, "so cannot add", document);
        // TODO - disallow?
        // verifyThat(name().equals(document.name()),
        // "schema " + name() + " can only contain document named " + name() + ", was: "+ document.name());
        this.myDocument = document;
    }

    void addFieldSet(ParsedFieldSet fieldSet) {
        String fsName = fieldSet.name();
        verifyThat(! fieldSets.containsKey(fsName), "already has fieldset", fsName);
        fieldSets.put(fsName, fieldSet);
    }
}
