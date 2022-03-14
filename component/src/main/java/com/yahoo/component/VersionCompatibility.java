// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.component;

import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * Logic for what platform and compile versions are incompatible.
 *
 * This class is instantiated with a list of versions which are only compatible with other versions
 * that are not older than themselves. Each entry in this list is represented by a string, and any
 * major, minor or micro with the value @{code *} means all versions are such boundaries.
 * Versions A, B are incompatible iff. there exists an incompatibility boundary X such that
 * (A &ge; X) &ne; (B &ge; X).
 *
 * @author jonmv
 */
public class VersionCompatibility {

    private final Node root;

    private VersionCompatibility(Node root) {
        this.root = root;
    }

    public static VersionCompatibility fromVersionList(List<String> versions) {
        Node root = new Node();
        for (String spec : versions) {
            String[] parts = spec.split("\\.");
            if (parts.length < 1 || parts.length > 3)
                throw new IllegalArgumentException("Each spec must have 1 to 3 parts, but found '" + spec + "'");

            boolean wildcard = false;
            Node node = root;
            for (int i = 0; i < 3; i++) {
                String part = i < parts.length ? parts[i] : wildcard ? null : "0";
                if (wildcard && part != null && ! part.equals("*"))
                    throw new IllegalArgumentException("Wildcard parts may only have wildcard children, but found '" + spec + "'");

                if ("*".equals(part)) {
                    wildcard = true;
                    if (node.children.isEmpty())
                        node = node.children.computeIfAbsent(-1, __ -> new Node());
                    else
                        throw new IllegalArgumentException("Wildcards may not have siblings, but got: " + versions);
                }
                else if (part != null) {
                    int number = Integer.parseInt(part);
                    if (number < 0)
                        throw new IllegalArgumentException("Version parts must be non-negative, but found '" + spec + "'");
                    if (node.children.containsKey(-1))
                        throw new IllegalArgumentException("Wildcards may not have siblings, but got: " + versions);
                    if (i < 2)
                        node = node.children.computeIfAbsent(number, __ -> new Node());
                    else if (node.children.put(number, new Node()) != null)
                        throw new IllegalArgumentException("Duplicate element '" + spec + "'");
                }
            }
        }
        return new VersionCompatibility(root);
    }

    public boolean accept(Version first, Version second) {
        return ! refuse(first, second);
    }

    public boolean refuse(Version first, Version second) {
        if (first.compareTo(second) > 0)
            return refuse(second, first);

        if (first.compareTo(second) == 0)
            return false;

        return refuse(new int[]{  first.getMajor(),  first.getMinor(),  first.getMicro() },
                      new int[]{ second.getMajor(), second.getMinor(), second.getMicro() },
                      0, root, root);
    }

    private boolean refuse(int[] first, int[] second, int i, Node left, Node right) {
        if (left == null && right == null) return false;
        if (i == 3) return right != null;
        int u = first[i], v = second[i];
        if (left == right) {
            Node wildcard = left.children.get(-1);
            if (wildcard != null)
                return u != v || refuse(first, second, i + 1, wildcard, wildcard);

            if ( ! left.children.tailMap(u, false).headMap(v, false).isEmpty())
                return true;

            return refuse(first, second, i + 1, left.children.get(u), left.children.get(v));
        }
        if (left != null && (left.children.containsKey(-1) || ! left.children.tailMap(u, false).isEmpty()))
            return true;

        if (right != null && (right.children.containsKey(-1) || ! right.children.headMap(v, false).isEmpty()))
            return true;

        return refuse(first, second, i + 1, left == null ? null : left.children.get(u), right == null ? null : right.children.get(v));
    }

    private static class Node {

        final NavigableMap<Integer, Node> children = new TreeMap<>();

    }

}
