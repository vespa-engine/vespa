// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.dispatch;

import com.google.common.collect.ImmutableMap;
import com.yahoo.collections.Pair;
import com.yahoo.search.dispatch.SearchCluster.Group;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
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
    public static List<SearchCluster.Node> selectNodes(String searchPath, SearchCluster cluster) {
        Optional<SearchPath> sp = SearchPath.fromString(searchPath);
        if (sp.isPresent()) {
            return sp.get().mapToNodes(cluster);
        } else {
            return Collections.emptyList();
        }
    }

    public static Optional<SearchPath> fromString(String path) {
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
            throw new InvalidSearchPathException("Invalid search path: " + path);
        }
    }

    private final List<Part> parts;
    private final Integer row;

    private SearchPath(List<Part> parts, Integer row) {
        this.parts = parts;
        this.row = row;
    }

    private List<SearchCluster.Node> mapToNodes(SearchCluster cluster) {
        if (cluster.groups().isEmpty()) {
            return Collections.emptyList();
        }

        SearchCluster.Group group = selectGroup(cluster);

        if (parts.isEmpty()) {
            return group.nodes();
        }
        Set<Integer> wanted = new HashSet<>();
        int max = group.nodes().size();
        for (Part part : parts) {
            wanted.addAll(part.matches(max));
        }
        // ordering by distribution key might not be equal to ordering in services.xml
        List<SearchCluster.Node> sortedByDistKey = new ArrayList<>(group.nodes());
        sortedByDistKey.sort(Comparator.comparingInt(SearchCluster.Node::key));

        List<SearchCluster.Node> ret = new ArrayList<>();
        for (int idx : wanted) {
            ret.add(sortedByDistKey.get(idx));
        }
        return ret;
    }

    private boolean isEmpty() {
        return parts.isEmpty() && row == null;
    }

    private Group selectGroup(SearchCluster cluster) {
        // ordering by group id might not be equal to ordering in services.xml
        ImmutableMap<Integer, SearchCluster.Group> byId = cluster.groups();
        List<Integer> sortedKeys = new ArrayList<>(byId.keySet());
        Collections.sort(sortedKeys);

        if (row != null && row < sortedKeys.size()) {
            return byId.get(sortedKeys.get(row));
        }

        // pick "anything": try to find the first working
        for (Integer id : sortedKeys) {
            SearchCluster.Group g = byId.get(id);
            if (g.hasSufficientCoverage()) {
                return g;
            }
        }
        // fallback: first
        return byId.get(sortedKeys.get(0));
    }

    private static SearchPath parseElement(String element) {
        Pair<String, String> partAndRow = halveAt('/', element);
        List<Part> parts = parseParts(partAndRow.getFirst());
        Integer row = parseRow(partAndRow.getSecond());

        return new SearchPath(parts, row);
    }

    private static List<Part> parseParts(String parts) {
        List<Part> ret = new ArrayList<>();
        while (parts.length() > 0) {
            if (parts.startsWith("[")) {
                parts = parsePartRange(parts, ret);
            } else {
                if (isWildcard(parts)) { // any part will be accepted
                    return Collections.emptyList();
                }
                parts = parsePartNum(parts, ret);
            }
        }
        return ret;
    }

    // an asterisk or an empty string followed by a comma or the end of the string
    private static final Pattern WILDCARD_PART = Pattern.compile("^\\*?(?:,|$)");

    private static boolean isWildcard(String part) {
        return WILDCARD_PART.matcher(part).lookingAt();
    }

    private static final Pattern PART_RANGE = Pattern.compile("^\\[(\\d+),(\\d+)>(?:,|$)");

    private static String parsePartRange(String parts, List<Part> into) {
        Matcher m = PART_RANGE.matcher(parts);
        if (m.find()) {
            String ret = parts.substring(m.end());
            Integer start = Integer.parseInt(m.group(1));
            Integer end = Integer.parseInt(m.group(2));
            if (start > end) {
                throw new InvalidSearchPathException("Invalid range");
            }
            into.add(new Part(start, end));
            return ret;
        } else {
            throw new InvalidSearchPathException("Invalid range expression");
        }
    }

    private static String parsePartNum(String parts, List<Part> into) {
        Pair<String, String> numAndRest = halveAt(',', parts);
        int partNum = Integer.parseInt(numAndRest.getFirst());
        into.add(new Part(partNum, partNum + 1));

        return numAndRest.getSecond();
    }

    private static Integer parseRow(String row) {
        if (row.isEmpty()) {
            return null;
        }
        if ("/".equals(row) || "*".equals(row)) { // anything goes
            return null;
        }
        return Integer.parseInt(row);
    }

    private static Pair<String, String> halveAt(char divider, String string) {
        int pos = string.indexOf(divider);
        if (pos >= 0) {
            return new Pair<>(string.substring(0, pos), string.substring(pos + 1, string.length()));
        }
        return new Pair<>(string, "");
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Part p : parts) {
            if (first) {
                first = false;
            } else {
                sb.append(',');
            }
            sb.append(p.toString());
        }
        if (row != null) {
            sb.append('/').append(row);
        }
        return sb.toString();
    }

    private static class Part {
        private final int from;
        private final int to;

        Part(int from, int to) {
            this.from = from;
            this.to = to;
        }

        public Collection<Integer> matches(int max) {
            if (from >= max) {
                return Collections.emptyList();
            }
            int end = (to > max) ? max : to;
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
    }

}
