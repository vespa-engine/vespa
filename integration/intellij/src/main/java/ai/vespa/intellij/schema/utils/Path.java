// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.intellij.schema.utils;

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
 * @author bratseth
 */
public final class Path {

    private final String delimiter;
    private final List<String> elements;

    /** Creates an empty path */
    private Path(String delimiter) {
        this(new ArrayList<>(), delimiter);
    }

    /**
     * Create a new path as a copy of the provided path
     *
     * @param path the path to copy
     */
    private Path(Path path) {
        this(path.elements, path.delimiter);
    }

    /**
     * Create path with given elements
     *
     * @param elements a list of path elements
     */
    private Path(List<String> elements, String delimiter) {
        this.elements = List.copyOf(elements);
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
     *
     * @param path the path to append
     * @return a new path with argument appended to it
     */
    public Path append(Path path) {
        List<String> newElements = new ArrayList<>(this.elements);
        newElements.addAll(path.elements());
        return new Path(newElements, delimiter);
    }

    /** Returns the name of this path element, typically the last element in the path string */
    public String getName() {
        if (elements.isEmpty()) return "";
        return elements.get(elements.size() - 1);
    }

    /** Returns a string representation of the path represented by this */
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

    /** Returns the parent path: A path containing all elements of this except the last */
    public Path getParentPath() {
        ArrayList<String> parentElements = new ArrayList<>();
        if (elements.size() > 1) {
            for (int i = 0; i < elements.size() - 1; i++) {
                parentElements.add(elements.get(i));
            }
        }
        return new Path(parentElements, delimiter);
    }

    /** Returns the child path: A path containing all elements of this except the first */
    public Path getChildPath() {
        ArrayList<String> childElements = new ArrayList<>();
        if (elements.size() > 1) {
            for (int i = 1; i < elements.size(); i++) {
                childElements.add(elements.get(i));
            }
        }
        return new Path(childElements, delimiter);
    }

    /** Returns the last element in this, or the empty string if this path is empty */
    public String last() {
        if (elements.isEmpty()) return "";
        return elements.get(elements.size() - 1);
    }

    /**
     * Returns a new path with the last element replaced by the given element.
     *
     * @throws IllegalStateException if this path is empty
     */
    public Path withLast(String element) {
        if (element.isEmpty()) throw new IllegalStateException("Cannot set the last element of an empty path");
        List<String> newElements = new ArrayList<>(elements);
        newElements.set(newElements.size() -1, element);
        return new Path(newElements, delimiter);
    }

    /** Returns a string representation of this path where the delimiter is prepended */
    public String getAbsolute() {
        return delimiter + getRelative();
    }

    public boolean isRoot() {
        return elements.isEmpty();
    }

    public Iterator<String> iterator() { return elements.iterator(); }

    /** Returns an immutable list of the elements of this path in order */
    public List<String> elements() { return elements; }

    /** Returns the extension of this file name, or an empty string if none. */
    public String extension() {
        int dotIndex = last().lastIndexOf('.');
        if (dotIndex < 0) return "";
        return last().substring(dotIndex + 1);
    }

    /** Returns this as a string */
    @Override
    public String toString() {
        return getRelative();
    }

    /**
     * Creates a path from a string. The string is treated as a relative path, and all redundant '/'-characters are
     * stripped.
     *
     * @param path the relative path that this path should represent
     * @return a path object that may be used with the application package
     */
    public static Path fromString(String path) {
        return fromString(path, "/");
    }

    /**
     * Create a path from a string. The string is treated as a relative path, and all redundant delimiter-characters are
     * stripped.
     *
     * @param path the relative path that this path should represent
     * @return a path object that may be used with the application package
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
