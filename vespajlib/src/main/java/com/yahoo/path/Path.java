// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.path;

import com.google.common.annotations.Beta;
import com.google.common.collect.ImmutableList;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Represents a path represented by a list of elements. Immutable
 *
 * @author Ulf Lilleengen
 */
@Beta
public final class Path {

    private final String delimiter;
    private final ImmutableList<String> elements;

    /**
     * Create an empty path.
     */
    private Path(String delimiter) {
        this(new ArrayList<>(), delimiter);
    }

    /**
     * Create a new path as a copy of the provided path.
     * @param rhs the path to copy.
     */
    private Path(Path rhs) {
        this(rhs.elements, rhs.delimiter);
    }

    /**
     * Create path with given elements.
     * @param elements a list of path elements
     */
    private Path(List<String> elements, String delimiter) {
        this.elements = ImmutableList.copyOf(elements);
        this.delimiter = delimiter;
    }

    /** Returns whether this path is an immediate child of the given path */
    public boolean isChildOf(Path parent) {
        return toString().startsWith(parent.toString()) && this.elements.size() -1 == parent.elements.size();
    }

    /**
     * Add path elements by splitting based on delimiter and appending to elements.
     */
    private static List<String> elementsOf(String path, String delimiter) {
        return Arrays.stream(path.split(delimiter)).filter(e -> !"".equals(e)).collect(Collectors.toList());
    }

    /**
     * Append an element to the path. Returns a new path with the given path appended.
     *
     * @param path the path to append to this
     * @return the new path
     */
    public Path append(String path) {
        List<String> newElements = new ArrayList<>(this.elements);
        newElements.addAll(elementsOf(path, delimiter));
        return new Path(newElements, delimiter);
    }

    /**
     * Appends a path to another path, thereby creating a new path with the provided path
     * appended to this.
     * @param path The path to append.
     * @return a new path with argument appended to it.
     */
    public Path append(Path path) {
        List<String> newElements = new ArrayList<>(this.elements);
        newElements.addAll(path.elements());
        return new Path(newElements, delimiter);
    }

    /**
     * Get the name of this path element, typically the last element in the path string.
     * @return the name
     */
    public String getName() {
        if (elements.isEmpty()) return "";
        return elements.get(elements.size() - 1);
    }

    /**
     * Get a string representation of the path represented by this.
     * @return a path string.
     */
    public String getRelative() {
        if (elements.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(elements.get(0));
        for (int i = 1; i < elements.size(); i++) {
            sb.append(delimiter);
            sb.append(elements.get(i));
        }
        return sb.toString();
    }

    /**
     * Get the parent path (all elements except last).
     * @return the parent path.
     */
    public Path getParentPath() {
        ArrayList<String> parentElements = new ArrayList<>();
        if (elements.size() > 1) {
            for (int i = 0; i < elements.size() - 1; i++) {
                parentElements.add(elements.get(i));
            }
        }
        return new Path(parentElements, delimiter);
    }

    /**
     * Get string representation of path represented from the root node.
     * @return string representation of path
     */
    public String getAbsolute() {
        return delimiter + getRelative();
    }

    public boolean isRoot() {
        return elements.isEmpty();
    }

    public Iterator<String> iterator() { return elements.iterator(); }

    /** Returns an immutable list of the elements of this path in order */
    public List<String> elements() { return elements; }

    /**
     * Convert to string.
     *
     * @return string representation of relative path
     */
    @Override
    public String toString() {
        // TODO: This and the relative/absolute thing is wrong. The Path either *is* relative or absolute
        //       and should return accordingly here. getAbsolute/relative should be replaced by an asRelative/absolute
        //       returning another Path
        return getRelative();
    }

    /**
     * Create a path from a string. The string is treated as a relative path, and all redundant '/'-characters are
     * stripped.
     * @param path the relative path that this path should represent.
     * @return a path object that may be used with the application package.
     */
    public static Path fromString(String path) {
        return fromString(path, "/");
    }

    /**
     * Create a path from a string. The string is treated as a relative path, and all redundant delimiter-characters are
     * stripped.
     * @param path the relative path that this path should represent.
     * @return a path object that may be used with the application package.
     */
    public static Path fromString(String path, String delimiter) {
        return new Path(elementsOf(path, delimiter), delimiter);
    }

    /**
     * Create an empty root path with '/' delimiter.
     *
     * @return an empty root path that can be appended
     */
    public static Path createRoot() {
        return createRoot("/");
    }

    /**
     * Create an empty root path with delimiter.
     *
     * @return an empty root path that can be appended
     */
    public static Path createRoot(String delimiter) {
        return new Path(delimiter);
    }

    public File toFile() { return new File(toString()); }

    @Override
    public int hashCode() {
        return elements.hashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof Path) {
            return getRelative().equals(((Path) other).getRelative());
        }
        return false;
    }
}
