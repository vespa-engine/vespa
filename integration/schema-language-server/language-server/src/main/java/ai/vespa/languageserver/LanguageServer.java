package ai.vespa.languageserver;

import ai.vespa.languageserver.parser.SchemaParser;
import ai.vespa.languageserver.parser.ParseException;
import ai.vespa.languageserver.parser.Node;

public class LanguageServer {

    public static void main(String[] args) {
        String input = "schema foo {\n \n}";

        var parser = new SchemaParser("test-input-str", input);

        try {
            parser.Root();
            Node root = parser.rootNode();
            root.dump();
        } catch(ParseException pe) {
            System.err.println(pe.getMessage());
        }
    }
}
