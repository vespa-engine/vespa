package ai.vespa.schemals.index;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
        documentInherits = new HashMap<>();
    }

    public void removeDocument(String fileURI) {
        documentInherits.remove(fileURI);
    }

    public void addInherits(String childURI, String parentURI) {
        createNodeIfNotExists(childURI);
        createNodeIfNotExists(parentURI);

        List<String> parentList = documentInherits.get(childURI);
        if (parentList.contains(parentURI)) return;
        parentList.add(parentURI);

    }

    public List<String> getAllInheritedDocumentURIs(String fileURI) {
        List<String> result = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        getAllInheritedDocumentURIsImpl(fileURI, result, visited);
        return result;
    }

    /*
     * Recursive search upwards through the inheritance graph to
     * retreive all ancestors of the given node.
     */
    private void getAllInheritedDocumentURIsImpl(String fileURI, List<String> result, Set<String> visited) {
        if (!documentInherits.containsKey(fileURI)) return;
        if (visited.contains(fileURI)) return;

        visited.add(fileURI);

        for (String parentURI : documentInherits.get(fileURI)) {
            getAllInheritedDocumentURIsImpl(parentURI, result, visited);
        }
        result.add(fileURI);
    }

    private void createNodeIfNotExists(String fileURI) {
        if (!documentInherits.containsKey(fileURI)) {
            documentInherits.put(fileURI, new ArrayList<>());
        }
    }
}
