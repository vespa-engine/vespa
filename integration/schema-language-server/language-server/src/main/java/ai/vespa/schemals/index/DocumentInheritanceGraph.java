package ai.vespa.schemals.index;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/*
 * This class is responsible for managing inheritance relationships among documents
 * Each node is identified by a file URI
 *
 * @author Mangern
 */
public class DocumentInheritanceGraph {

    private HashMap<String, List<String>> documentParents = new HashMap<>();
    private HashMap<String, List<String>> documentChildren = new HashMap<>();

    public DocumentInheritanceGraph() { }

    public void clearInheritsList(String fileURI) {
        if (!nodeExists(fileURI)) return;

        // Remove myself from my parents children
        for (String parent : documentParents.get(fileURI)) {
            documentChildren.get(parent).remove(fileURI);
        }

        documentParents.remove(fileURI);
    }

    public void createNodeIfNotExists(String fileURI) {
        if (!documentParents.containsKey(fileURI)) {
            documentParents.put(fileURI, new ArrayList<>());
        }

        if (!documentChildren.containsKey(fileURI)) {
            documentChildren.put(fileURI, new ArrayList<>());
        }
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

    /*
     * Gets a list of all direct or indirect inheritance ancestors.
     * The returned list is in a topological order, i.e. the older ancestors
     * come earler in the list.
     * List includes the queried node (will be last)
     */
    public List<String> getAllDocumentAncestorURIs(String fileURI) {
        createNodeIfNotExists(fileURI);

        List<String> result = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        getAllDocumentAncestorURIsImpl(fileURI, result, visited);
        return result;
    }

    /*
     * Gets a list of all direct or indirect inheritance ancestors.
     * The returned list is in a topological order, i.e. the older descendants
     * come earler in the list.
     * List includes the queried node (will be first)
     */
    public List<String> getAllDocumentDescendantURIs(String fileURI) {
        createNodeIfNotExists(fileURI);

        List<String> result = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        getAllDocumentDescendantURIsImpl(fileURI, result, visited);
        return result;
    }

    /*
     * Returns a list of all registered documents in topological order
     */
    public List<String> getAllDocumentsTopoOrder() {
        Set<String> visited = new HashSet<>();

        List<String> result = new LinkedList<>();
        for (String fileURI : documentParents.keySet()) {
            if (visited.contains(fileURI)) continue;

            getAllDocumentAncestorURIsImpl(fileURI, result, visited);
        }

        return result;
    }

    private String getFileName(String fileURI) {
        Integer splitPos = fileURI.lastIndexOf('/');
        return fileURI.substring(splitPos + 1);
    }

    public void dumpAllEdges(PrintStream logger) {
        for (Map.Entry<String, List<String>> entry : documentParents.entrySet()) {
            String childURI = entry.getKey();
            logger.println(getFileName(childURI) + " inherits from:");
            for (String parentURI : entry.getValue()) {
                logger.println("    " + getFileName(parentURI));
            }
        }

        for (String parentURI : documentChildren.keySet()) {
            logger.println(getFileName(parentURI) + " has children:");
            for (String childURI : getValidChildren(parentURI)) {
                logger.println("    " + getFileName(childURI));
            }
        }
        logger.println();

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

        result.add(fileURI);

        for (String childURI : getValidChildren(fileURI)) {
            getAllDocumentDescendantURIsImpl(childURI, result, visited);
        }

    }

    private boolean nodeExists(String fileURI) {
        return documentParents.containsKey(fileURI) && documentChildren.containsKey(fileURI);
    }

    private List<String> getValidChildren(String fileURI) {
        return documentChildren.getOrDefault(fileURI, new ArrayList<>())
                               .stream()
                               .filter(childURI -> documentParents.get(childURI).contains(fileURI))
                               .collect(Collectors.toList());
    }
}
