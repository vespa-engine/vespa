package ai.vespa.schemals.index;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;


/**
 * This class is responsible for managing inheritance relationships among generic constructs.
 * Each node is identified by a NodeType which should implement a good hashCode and .equals.
 *
 * @author Mangern
 */
public class InheritanceGraph<NodeType> {

    protected Map<NodeType, List<NodeType>> parentsOfNode = new HashMap<>();
    protected Map<NodeType, List<NodeType>> childrenOfNode = new HashMap<>();

    public class SearchResult<ResultType> {
        public NodeType node = null;
        public ResultType result = null;

        public SearchResult(NodeType node, ResultType result) {
            this.node = node;
            this.result = result;
        }
    }

    public InheritanceGraph() { }

    public void clearInheritsList(NodeType node) {
        if (!nodeExists(node)) return;

        // Remove myself from my parents children
        for (NodeType parent : parentsOfNode.get(node)) {
            childrenOfNode.get(parent).remove(node);
        }

        parentsOfNode.remove(node);
    }

    public void createNodeIfNotExists(NodeType node) {
        if (!parentsOfNode.containsKey(node)) {
            parentsOfNode.put(node, new ArrayList<>());
        }

        if (!childrenOfNode.containsKey(node)) {
            childrenOfNode.put(node, new ArrayList<>());
        }
    }

    /**
     * Try to register a new inheritance relationship such that
     * childNode inherits from parentNode
     *
     * @return boolean indicating success. Inheritance is unsuccessful if the relationship would create a cycle 
     * in the inheritance graph.
     */
    public boolean addInherits(NodeType childNode, NodeType parentNode) {
        createNodeIfNotExists(childNode);
        createNodeIfNotExists(parentNode);

        List<NodeType> existingAncestors = getAllAncestors(parentNode);

        if (existingAncestors.contains(childNode)) {
            // childNode cannot inherit from parentNode if parentNode directly or indirectly inherits from childNode (cycle)
            return false;
        }

        List<NodeType> parentList = parentsOfNode.get(childNode);
        if (!parentList.contains(parentNode)) {
            parentList.add(parentNode);
        }

        List<NodeType> parentChildren = childrenOfNode.get(parentNode);

        if (!parentChildren.contains(childNode)) {
            parentChildren.add(childNode);
        }

        return true;
    }

    public List<NodeType> getAllParents(NodeType node) {
        createNodeIfNotExists(node);

        return parentsOfNode.get(node);
    }

    /**
     * Gets a list of all direct or indirect inheritance ancestors.
     * The returned list is in a topological order, i.e. the older ancestors
     * come earler in the list.
     * List includes the queried node (will be last)
     */
    public List<NodeType> getAllAncestors(NodeType node) {
        createNodeIfNotExists(node);

        List<NodeType> result = new ArrayList<>();
        Set<NodeType> visited = new HashSet<>();
        getAllAncestorsImpl(node, result, visited);
        return result;
    }

    /**
     * Gets a list of all direct or indirect inheritance ancestors.
     * The returned list is in a topological order, i.e. the older descendants
     * come earler in the list.
     * List includes the queried node (will be first)
     */
    public List<NodeType> getAllDescendants(NodeType node) {
        createNodeIfNotExists(node);

        List<NodeType> result = new ArrayList<>();
        Set<NodeType> visited = new HashSet<>();
        getAllDescendantsImpl(node, result, visited);
        return result;
    }

    /**
     * @return a list of all registered documents in topological order
     */
    public List<NodeType> getTopoOrdering() {
        Set<NodeType> visited = new HashSet<>();

        List<NodeType> result = new LinkedList<>();
        for (NodeType node : parentsOfNode.keySet()) {
            if (visited.contains(node)) continue;

            getAllAncestorsImpl(node, result, visited);
        }

        return result;
    }

    /**
     * Searches upwards for nodes where predicate(node) returns non null.
     * If a match is found, parents of that node are not checked (unless they are reachable by some other path without matches).
     * Return values store both the node and the result of running predicate on them
     */
    public <T> List<SearchResult<T>> findFirstMatches(NodeType node, Function<NodeType, T> predicate) {
        createNodeIfNotExists(node);

        Set<NodeType> visited = new HashSet<>();
        List<SearchResult<T>> result = new ArrayList<>();
        findFirstMatchesImpl(node, result, visited, predicate);
        return result;
    }

    private <T> void findFirstMatchesImpl(NodeType node, List<SearchResult<T>> result, Set<NodeType> visited, Function<NodeType, T> predicate) {
        if (!parentsOfNode.containsKey(node)) return;
        if (visited.contains(node)) return;

        visited.add(node);

        T match = predicate.apply(node);

        if (match != null) {
            result.add(new SearchResult<>(node, match));
            return;
        }

        for (NodeType parent : parentsOfNode.get(node)) {
            findFirstMatchesImpl(parent, result, visited, predicate);
        }
    }
    
    /**
     * Recursive search upwards through the inheritance graph to
     * retreive all ancestors of the given node.
     */
    private void getAllAncestorsImpl(NodeType node, List<NodeType> result, Set<NodeType> visited) {
        if (!parentsOfNode.containsKey(node)) return;
        if (visited.contains(node)) return;

        visited.add(node);

        for (NodeType parentNode : parentsOfNode.get(node)) {
            getAllAncestorsImpl(parentNode, result, visited);
        }
        result.add(node);
    }

    /**
     * Recursive search downwards through the inheritance graph to 
     * retrieve all who directly or indirectly inherits the given node
     */
    private void getAllDescendantsImpl(NodeType node, List<NodeType> result, Set<NodeType> visited) {
        if (!childrenOfNode.containsKey(node)) return;
        if (visited.contains(node)) return;

        visited.add(node);

        result.add(node);

        for (NodeType childNode : getValidChildren(node)) {
            getAllDescendantsImpl(childNode, result, visited);
        }

    }

    private boolean nodeExists(NodeType node) {
        return parentsOfNode.containsKey(node) && childrenOfNode.containsKey(node);
    }

    private List<NodeType> getValidChildren(NodeType node) {
        return childrenOfNode.getOrDefault(node, new ArrayList<>())
                               .stream()
                               .filter(childNode -> parentsOfNode.get(childNode).contains(node))
                               .collect(Collectors.toList());
    }

    public void dumpAllEdges(PrintStream logger) {
        for (Map.Entry<NodeType, List<NodeType>> entry : parentsOfNode.entrySet()) {
            NodeType node = entry.getKey();
            logger.println(node.toString() + " inherits from:");
            for (NodeType parent : entry.getValue()) {
                logger.println("    " + parent.toString());
            }
        }

        for (NodeType parentURI : childrenOfNode.keySet()) {
            logger.println(parentURI.toString() + " has children:");
            for (NodeType childURI : getValidChildren(parentURI)) {
                logger.println("    " + childURI.toString());
            }
        }
        logger.println();

    }
}
