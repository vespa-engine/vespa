// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.dispatch;

import com.yahoo.collections.Pair;
import com.yahoo.search.dispatch.searchcluster.Group;
import com.yahoo.search.dispatch.searchcluster.Node;
import com.yahoo.search.dispatch.searchcluster.SearchCluster;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Utility class for parsing model.searchPath and filtering a search cluster
 * based on it.
 *
 * @author ollivir
 */
public class SearchPath {

    /**
     * Parse the search path and select nodes from the given cluster based on it.
     *
     * @param searchPath
     *            unparsed search path expression (see: model.searchPath in Search
     *            API reference)
     * @param cluster
     *            the search cluster from which nodes are selected
     * @throws InvalidSearchPathException
     *             if the searchPath is malformed
     * @return list of nodes chosen with the search path, or an empty list in which
     *         case some other node selection logic should be used
     */
    public static List<Node> selectNodes(String searchPath, SearchCluster cluster) {
        Optional<SearchPath> sp = SearchPath.fromString(searchPath);
        if (sp.isPresent()) {
            return sp.get().mapToNodes(cluster);
        } else {
            return Collections.emptyList();
        }
    }

    static Optional<SearchPath> fromString(String path) {
        if (path == null || path.isEmpty()) {
            return Optional.empty();
        }
        if (path.indexOf(';') >= 0) {
            return Optional.empty(); // multi-level not supported at this time
        }
        try {
            SearchPath sp = parseElement(path);
            if (sp.isEmpty()) {
                return Optional.empty();
            } else {
                return Optional.of(sp);
            }
        } catch (NumberFormatException | InvalidSearchPathException e) {
            throw new InvalidSearchPathException("Invalid search path: " + path, e);
        }
    }

    private final List<Selection> nodes;
    private final List<Selection> groups;
    private static final Random random = new Random();

    private SearchPath(List<Selection> nodes, List<Selection> groups) {
        this.nodes = nodes;
        this.groups = groups;
    }

    private List<Node> mapToNodes(SearchCluster cluster) {
        if (cluster.groups().isEmpty()) {
            return Collections.emptyList();
        }

        Group selectedGroup = selectGroup(cluster);

        if (nodes.isEmpty()) {
            return selectedGroup.nodes();
        }
        List<Node> groupNodes = selectedGroup.nodes();
        Set<Integer> wanted = new HashSet<>();
        int max = groupNodes.size();
        for (Selection node : nodes) {
            wanted.addAll(node.matches(max));
        }
        List<Node> ret = new ArrayList<>();
        for (int idx : wanted) {
            ret.add(groupNodes.get(idx));
        }
        return ret;
    }

    private boolean isEmpty() {
        return nodes.isEmpty() && groups.isEmpty();
    }

    private Group selectRandomGroupWithSufficientCoverage(SearchCluster cluster, List<Integer> groupIds) {
        while ( groupIds.size() > 1 ) {
            int index = random.nextInt(groupIds.size());
            int groupId = groupIds.get(index);
            Optional<Group> group = cluster.group(groupId);
            if (group.isPresent()) {
                if (group.get().hasSufficientCoverage()) {
                    return group.get();
                } else {
                    groupIds.remove(index);
                }
            } else {
                throw new InvalidSearchPathException("Invalid searchPath, cluster does not have " + (groupId + 1) + " groups");
            }
        }
        return cluster.group(groupIds.get(0)).get();
    }

    private Group selectGroup(SearchCluster cluster) {
        if ( ! groups.isEmpty()) {
            List<Integer> potentialGroups = new ArrayList<>();
            for (Selection groupSelection : groups) {
                for (int group = groupSelection.from; group < groupSelection.to; group++) {
                    potentialGroups.add(group);
                }
            }
            return selectRandomGroupWithSufficientCoverage(cluster, potentialGroups);
        }

        // pick any working group
        return selectRandomGroupWithSufficientCoverage(cluster, new ArrayList<>(cluster.groups().keySet()));
    }

    private static SearchPath parseElement(String element) {
        Pair<String, String> nodesAndGroups = halveAt('/', element);
        List<Selection> nodes = parseSelection(nodesAndGroups.getFirst());
        List<Selection> groups = parseSelection(nodesAndGroups.getSecond());

        return new SearchPath(nodes, groups);
    }

    private static List<Selection> parseSelection(String nodes) {
        List<Selection> ret = new ArrayList<>();
        while (nodes.length() > 0) {
            if (nodes.startsWith("[")) {
                nodes = parseNodeRange(nodes, ret);
            } else {
                if (isWildcard(nodes)) { // any node will be accepted
                    return Collections.emptyList();
                }
                nodes = parseNodeNum(nodes, ret);
            }
        }
        return ret;
    }

    // An asterisk or forward slash or an empty string followed by a comma or the end of the string
    private static final Pattern NODE_WILDCARD = Pattern.compile("^\\*?(?:,|$|/$)");

    private static boolean isWildcard(String node) {
        return NODE_WILDCARD.matcher(node).lookingAt();
    }

    private static final Pattern NODE_RANGE = Pattern.compile("^\\[(\\d+),(\\d+)>(?:,|$)");

    private static String parseNodeRange(String nodes, List<Selection> into) {
        Matcher m = NODE_RANGE.matcher(nodes);
        if (m.find()) {
            String ret = nodes.substring(m.end());
            int start = Integer.parseInt(m.group(1));
            int end = Integer.parseInt(m.group(2));
            if (start > end) {
                throw new InvalidSearchPathException("Invalid range");
            }
            into.add(new Selection(start, end));
            return ret;
        } else {
            throw new InvalidSearchPathException("Invalid range expression");
        }
    }

    private static String parseNodeNum(String nodes, List<Selection> into) {
        Pair<String, String> numAndRest = halveAt(',', nodes);
        int nodeNum = Integer.parseInt(numAndRest.getFirst());
        into.add(new Selection(nodeNum, nodeNum + 1));

        return numAndRest.getSecond();
    }

    private static Pair<String, String> halveAt(char divider, String string) {
        int pos = string.indexOf(divider);
        if (pos >= 0) {
            return new Pair<>(string.substring(0, pos), string.substring(pos + 1));
        }
        return new Pair<>(string, "");
    }

    private static void selectionToString(StringBuilder sb, List<Selection> nodes) {
        boolean first = true;
        for (Selection p : nodes) {
            if (first) {
                first = false;
            } else {
                sb.append(',');
            }
            sb.append(p.toString());
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        selectionToString(sb, nodes);
        if ( ! groups.isEmpty()) {
            sb.append('/');
            selectionToString(sb, groups);
        }
        return sb.toString();
    }

    private static class Selection {
        private final int from;
        private final int to;

        Selection(int from, int to) {
            this.from = from;
            this.to = to;
        }

        public Collection<Integer> matches(int max) {
            if (from >= max) {
                return Collections.emptyList();
            }
            int end = Math.min(to, max);
            return IntStream.range(from, end).boxed().collect(Collectors.toList());
        }

        @Override
        public String toString() {
            if (from + 1 == to) {
                return Integer.toString(from);
            } else {
                return "[" + from + "," + to + ">";
            }
        }
    }

    public static class InvalidSearchPathException extends RuntimeException {
        public InvalidSearchPathException(String message) {
            super(message);
        }

        public InvalidSearchPathException(String message, Throwable cause) {
            super(message, cause);
        }
    }

}
