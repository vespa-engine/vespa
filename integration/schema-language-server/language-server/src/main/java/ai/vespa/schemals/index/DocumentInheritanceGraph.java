package ai.vespa.schemals.index;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/*
 * This class is responsible for managing inheritance relationships among documents
 * Each node is identified by a file URI
 *
 * @author Mangern
 */
public class DocumentInheritanceGraph {

    private PrintStream logger;

    private HashMap<String, List<String>> documentInherits;

    public DocumentInheritanceGraph(PrintStream logger) {
        this.logger = logger;
    }

    public void setInherits(String fileURI, List<String> inheritsURIs) {
        if (documentInherits.containsKey(fileURI)) {
            documentInherits.get(fileURI).clear();
        } else {
            documentInherits.put(fileURI, new ArrayList<>());
        }

        documentInherits.get(fileURI).addAll(inheritsURIs);
    }
}
