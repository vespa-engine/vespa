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
public class InheritanceGraph {

    private Map<String, List<String>> parentsOfNode = new HashMap<>();
    private Map<String, List<String>> childrenOfNode = new HashMap<>();

    public InheritanceGraph() { }

    public void clearInheritsList(String node) {
        if (!nodeExists(node)) return;

        // Remove myself from my parents children
        for (String parent : parentsOfNode.get(node)) {
            childrenOfNode.get(parent).remove(node);
        }

        parentsOfNode.remove(node);
    }

    public void createNodeIfNotExists(String node) {
        if (!parentsOfNode.containsKey(node)) {
            parentsOfNode.put(node, new ArrayList<>());
        }

        if (!childrenOfNode.containsKey(node)) {
            childrenOfNode.put(node, new ArrayList<>());
        }
    }

    /*
     * Try to register a new inheritance relationship such that
     * childNode inherits from parentNode
     *
     * @return boolean indicating success. Inheritance is unsuccessful if the relationship would create a cycle 
     * in the inheritance graph.
     */
    public boolean addInherits(String childNode, String parentNode) {
        createNodeIfNotExists(childNode);
        createNodeIfNotExists(parentNode);

        List<String> existingAncestors = getAllAncestors(parentNode);

        if (existingAncestors.contains(childNode)) {
            // childNode cannot inherit from parentNode if parentNode directly or indirectly inherits from childNode (cycle)
            return false;
        }

        List<String> parentList = parentsOfNode.get(childNode);
        if (!parentList.contains(parentNode)) {
            parentList.add(parentNode);
        }

        List<String> parentChildren = childrenOfNode.get(parentNode);

        if (!parentChildren.contains(childNode)) {
            parentChildren.add(childNode);
        }

        return true;
    }

    /*
     * Gets a list of all direct or indirect inheritance ancestors.
     * The returned list is in a topological order, i.e. the older ancestors
     * come earler in the list.
     * List includes the queried node (will be last)
     */
    public List<String> getAllAncestors(String node) {
        createNodeIfNotExists(node);

        List<String> result = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        getAllAncestorsImpl(node, result, visited);
        return result;
    }

    /*
     * Gets a list of all direct or indirect inheritance ancestors.
     * The returned list is in a topological order, i.e. the older descendants
     * come earler in the list.
     * List includes the queried node (will be first)
     */
    public List<String> getAllDescendants(String node) {
        createNodeIfNotExists(node);

        List<String> result = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        getAllDescendantsImpl(node, result, visited);
        return result;
    }

    /*
     * Returns a list of all registered documents in topological order
     */
    public List<String> getTopoOrdering() {
        Set<String> visited = new HashSet<>();

        List<String> result = new LinkedList<>();
        for (String node : parentsOfNode.keySet()) {
            if (visited.contains(node)) continue;

            getAllAncestorsImpl(node, result, visited);
        }

        return result;
    }

    private String getBaseName(String node) {
        Integer splitPos = node.lastIndexOf('/');
        return node.substring(splitPos + 1);
    }

    public void dumpAllEdges(PrintStream logger) {
        for (Map.Entry<String, List<String>> entry : parentsOfNode.entrySet()) {
            String childNode = entry.getKey();
            logger.println(getBaseName(childNode) + " inherits from:");
            for (String parentNode : entry.getValue()) {
                logger.println("    " + getBaseName(parentNode));
            }
        }

        for (String parentNode : childrenOfNode.keySet()) {
            logger.println(getBaseName(parentNode) + " has children:");
            for (String childNode : getValidChildren(parentNode)) {
                logger.println("    " + getBaseName(childNode));
            }
        }
        logger.println();

    }

    
    /*
     * Recursive search upwards through the inheritance graph to
     * retreive all ancestors of the given node.
     */
    private void getAllAncestorsImpl(String node, List<String> result, Set<String> visited) {
        if (!parentsOfNode.containsKey(node)) return;
        if (visited.contains(node)) return;

        visited.add(node);

        for (String parentNode : parentsOfNode.get(node)) {
            getAllAncestorsImpl(parentNode, result, visited);
        }
        result.add(node);
    }

    /*
     * Recursive search downwards through the inheritance graph to 
     * retrieve all who directly or indirectly inherits the given node
     */
    private void getAllDescendantsImpl(String node, List<String> result, Set<String> visited) {
        if (!childrenOfNode.containsKey(node)) return;
        if (visited.contains(node)) return;

        visited.add(node);

        result.add(node);

        for (String childNode : getValidChildren(node)) {
            getAllDescendantsImpl(childNode, result, visited);
        }

    }

    private boolean nodeExists(String node) {
        return parentsOfNode.containsKey(node) && childrenOfNode.containsKey(node);
    }

    private List<String> getValidChildren(String node) {
        return childrenOfNode.getOrDefault(node, new ArrayList<>())
                               .stream()
                               .filter(childNode -> parentsOfNode.get(childNode).contains(node))
                               .collect(Collectors.toList());
    }
}
