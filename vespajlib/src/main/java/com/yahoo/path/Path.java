// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.path;

import com.google.common.annotations.Beta;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

// TODO: Remove and replace usage by java.nio.file.Path

/**
 * Represents a path represented by a list of elements. Immutable
 *
 * @author lulf
 */
@Beta
public final class Path {

    private final String delimiter;
    private final List<String> elements = new ArrayList<>();

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
        this.elements.addAll(elements);
        this.delimiter = delimiter;
    }

    /** Returns whether this path is an immediate child of the given path */
    public boolean isChildOf(Path parent) {
        return toString().startsWith(parent.toString()) && this.elements.size() -1 == parent.elements.size();
    }

    /**
     * Add path elements by splitting based on delimiter and appending to elements.
     */
    private void addElementsFromString(String path) {
        String[] pathElements = path.split(delimiter);
        if (pathElements != null) {
            for (String elem : pathElements) {
                if (!"".equals(elem)) {
                    elements.add(elem);
                }
            }
        }
    }

    /**
     * Append an element to the path. Returns a new path with this element appended.
     * @param name name of element to append.
     * @return this, for chaining
     */
    public Path append(String name) {
        Path path = new Path(this);
        path.addElementsFromString(name);
        return path;
    }

    /**
     * Appends a path to another path, thereby creating a new path with the provided path
     * appended to this.
     * @param path The path to append.
     * @return a new path with argument appended to it.
     */
    public Path append(Path path) {
        Path newPath = new Path(this);
        newPath.elements.addAll(path.elements);
        return newPath;
    }

    /**
     * Get the name of this path element, typically the last element in the path string.
     * @return the name
     */
    public String getName() {
        if (elements.isEmpty()) {
            return "";
        }
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
        Path pathObj = new Path(delimiter);
        pathObj.addElementsFromString(path);
        return pathObj;
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
