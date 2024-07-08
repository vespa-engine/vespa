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

    private HashMap<String, List<String>> documentParents = new HashMap<>();
    private HashMap<String, List<String>> documentChildren = new HashMap<>();

    public DocumentInheritanceGraph(PrintStream logger) {
        this.logger = logger;
    }

    public void clearInheritsList(String fileURI) {
        if (!nodeExists(fileURI)) return;

        // Remove myself from my parents children
        for (String parent : documentParents.get(fileURI)) {
            documentChildren.get(parent).remove(fileURI);
        }

        documentParents.remove(fileURI);
    }

    /*
     * Try to register a new inheritance relationship such that
     * childURI inherits from parentURI
     *
     * @return boolean indicating success. Inheritance is unsuccessful if the relationship would create a cycle 
     * in the inheritance graph.
     */
    public boolean addInherits(String childURI, String parentURI) {
        createNodeIfNotExists(childURI);
        createNodeIfNotExists(parentURI);

        List<String> existingAncestors = getAllDocumentAncestorURIs(parentURI);

        if (existingAncestors.contains(childURI)) {
            // childURI cannot inherit from parentURI if parentURI directly or indirectly inherits from childURI (cycle)
            return false;
        }

        List<String> parentList = documentParents.get(childURI);
        if (!parentList.contains(parentURI)) {
            parentList.add(parentURI);
        }

        List<String> parentChildren = documentChildren.get(parentURI);

        if (!parentChildren.contains(childURI)) {
            parentChildren.add(childURI);
        }

        return true;
    }

    public List<String> getAllDocumentAncestorURIs(String fileURI) {
        List<String> result = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        getAllDocumentAncestorURIsImpl(fileURI, result, visited);
        return result;
    }

    public List<String> getAllDocumentDescendantURIs(String fileURI) {
        List<String> result = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        getAllDocumentDescendantURIsImpl(fileURI, result, visited);
        return result;
    }
    
    /*
     * Recursive search upwards through the inheritance graph to
     * retreive all ancestors of the given node.
     */
    private void getAllDocumentAncestorURIsImpl(String fileURI, List<String> result, Set<String> visited) {
        if (!documentParents.containsKey(fileURI)) return;
        if (visited.contains(fileURI)) return;

        visited.add(fileURI);

        for (String parentURI : documentParents.get(fileURI)) {
            getAllDocumentAncestorURIsImpl(parentURI, result, visited);
        }
        result.add(fileURI);
    }

    /*
     * Recursive search downwards through the inheritance graph to 
     * retrieve all who directly or indirectly inherits the given node
     */
    private void getAllDocumentDescendantURIsImpl(String fileURI, List<String> result, Set<String> visited) {
        if (!documentChildren.containsKey(fileURI)) return;
        if (visited.contains(fileURI)) return;

        visited.add(fileURI);

        for (String childURI : getChildren(fileURI)) {
            getAllDocumentDescendantURIsImpl(childURI, result, visited);
        }

        result.add(fileURI);
    }

    private boolean nodeExists(String fileURI) {
        return documentParents.containsKey(fileURI) && documentChildren.containsKey(fileURI);
    }

    private void createNodeIfNotExists(String fileURI) {
        if (!documentParents.containsKey(fileURI)) {
            documentParents.put(fileURI, new ArrayList<>());
        }

        if (!documentChildren.containsKey(fileURI)) {
            documentChildren.put(fileURI, new ArrayList<>());
        }
    }

    private List<String> getChildren(String fileURI) {
        List<String> childrenList = documentChildren.getOrDefault(fileURI, new ArrayList<>());

        List<String> correctList = new ArrayList<>();
        for (String childURI : childrenList) {
            if (documentParents.get(childURI).contains(fileURI)) {
                correctList.add(childURI);
            }
        }

        documentChildren.put(fileURI, correctList);
        return correctList;
    }
}
