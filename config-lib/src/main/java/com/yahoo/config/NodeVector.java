// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config;

import java.util.*;


/**
 * A NodeVector represents an array declared with '[]' in a config definition file.
 * It is a List that stores nodes with a given type. A given default node must
 * be given, and this node will be cloned as the NodeVector size are increased.
 *
 */
public abstract class NodeVector<NODE> implements java.util.List<NODE> {

    protected final ArrayList<NODE> vector = new ArrayList<>();

    /**
     * Returns the number of elements in this NodeVector.
     * Alias for size().
     *
     * @return the number of elements in this NodeVector.
     */
    public int length() {
        return size();
    }

    public static class ReadOnlyException extends RuntimeException {
    }

    private static final ReadOnlyException e = new ReadOnlyException();

    public void add(int index, NODE element) {
        throw e;
    }

    public boolean add(NODE o) {
        throw e;
    }

    public boolean addAll(Collection<? extends NODE> c) {
        throw e;
    }

    public boolean addAll(int index, Collection<? extends NODE> c) {
        throw e;
    }

    public void clear() {
        throw e;
    }

    public NODE remove(int index) {
        throw e;
    }

    public boolean remove(Object o) {
        throw e;
    }

    public boolean removeAll(Collection<?> c) {
        throw e;
    }

    public boolean retainAll(Collection<?> c) {
        throw e;
    }

    public NODE set(int index, NODE element) {
        throw e;
    }

    public boolean contains(Object o) {
        return vector.contains(o);
    }

    public boolean containsAll(Collection<?> c) {
        return vector.containsAll(c);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof NodeVector && vector.equals(((NodeVector) o).vector);
    }

    @Override
    public int hashCode() {
        return vector.hashCode();
    }

    public NODE get(int index) {
        return vector.get(index);
    }

    public int indexOf(Object o) {
        return vector.indexOf(o);
    }

    public boolean isEmpty() {
        return vector.isEmpty();
    }

    public Iterator<NODE> iterator() {
        return vector.iterator();
    }

    public int lastIndexOf(Object o) {
        return vector.lastIndexOf(o);
    }

    public ListIterator<NODE> listIterator() {
        return vector.listIterator();
    }

    public ListIterator<NODE> listIterator(int index) {
        return vector.listIterator(index);
    }

    public int size() {
        return vector.size();
    }

    public List<NODE> subList(int fromIndex, int toIndex) {
        return vector.subList(fromIndex, toIndex);
    }

    public Object[] toArray() {
        return vector.toArray();
    }

    public <T> T[] toArray(T[] a) {
        return vector.toArray(a);
    }
}
