// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.application.container.handler;

import com.yahoo.api.annotations.Beta;
import com.yahoo.jdisc.HeaderFields;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A multi-map for Request and Response header fields.
 *
 * @see Request
 * @see Response
 * @author Einar M R Rosenvinge
 * @author Simon Thoresen Hult
 */
@Beta
public class Headers implements Map<String, List<String>> {
    private final HeaderFields h = new HeaderFields();

    @Override
    public int size() {
        return h.size();
    }

    @Override
    public boolean isEmpty() {
        return h.isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        return h.containsKey(key);
    }

    @Override
    public boolean containsValue(Object value) {
        return h.containsValue(value);
    }

    @Override
    public List<String> get(Object key) {
        return h.get(key);
    }

    @Override
    public List<String> put(String key, List<String> value) {
        return h.put(key, value);
    }

    @Override
    public List<String> remove(Object key) {
        return h.remove(key);
    }

    @Override
    public void putAll(Map<? extends String, ? extends List<String>> m) {
        h.putAll(m);
    }

    @Override
    public void clear() {
        h.clear();
    }

    @Override
    public Set<String> keySet() {
        return h.keySet();
    }

    @Override
    public Collection<List<String>> values() {
        return h.values();
    }

    @Override
    public Set<Map.Entry<String, List<String>>> entrySet() {
        return h.entrySet();
    }

    @Override
    public String toString() {
        return h.toString();
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof Headers && h.equals(((Headers)obj).h);
    }

    @Override
    public int hashCode() {
        return h.hashCode();
    }


    /**
     * <p>Convenience method for checking whether or not a named header contains a specific value. If the named header
     * is not set, or if the given value is not contained within that header's value list, this method returns
     * <em>false</em>.</p>
     *
     * <p><em>NOTE:</em> This method is case-SENSITIVE.</p>
     *
     * @param key   The key whose values to search in.
     * @param value The values to search for.
     * @return True if the given value was found in the named header.
     * @see #containsIgnoreCase
     */
    public boolean contains(String key, String value) {
        return h.contains(key, value);
    }

    /**
     * <p>Convenience method for checking whether or not a named header contains a specific value, regardless of case.
     * If the named header is not set, or if the given value is not contained within that header's value list, this
     * method returns <em>false</em>.</p>
     *
     * <p><em>NOTE:</em> This method is case-INSENSITIVE.</p>
     *
     * @param key   The key whose values to search in.
     * @param value The values to search for, ignoring case.
     * @return True if the given value was found in the named header.
     * @see #contains
     */
    public boolean containsIgnoreCase(String key, String value) {
        return h.containsIgnoreCase(key, value);
    }

    /**
     * <p>Adds the given value to the entry of the specified key. If no entry exists for the given key, a new one is
     * created containing only the given value.</p>
     *
     * @param key   The key with which the specified value is to be associated.
     * @param value The value to be added to the list associated with the specified key.
     */
    public void add(String key, String value) {
        h.add(key, value);
    }

    /**
     * <p>Adds the given values to the entry of the specified key. If no entry exists for the given key, a new one is
     * created containing only the given values.</p>
     *
     * @param key    The key with which the specified value is to be associated.
     * @param values The values to be added to the list associated with the specified key.
     */
    public void add(String key, List<String> values) {
        h.add(key, values);
    }

    /**
     * <p>Adds all the entries of the given map to this. This is the same as calling {@link #add(String, List)} for each
     * entry in <code>values</code>.</p>
     *
     * @param values The values to be added to this.
     */
    public void addAll(Map<? extends String, ? extends List<String>> values) {
        h.addAll(values);
    }

    /**
     * <p>Convenience method to call {@link #put(String, List)} with a singleton list that contains the specified
     * value.</p>
     *
     * @param key   The key of the entry to put.
     * @param value The value to put.
     * @return The previous value associated with <code>key</code>, or <code>null</code> if there was no mapping for
     *         <code>key</code>.
     */
    public List<String> put(String key, String value) {
        return h.put(key, value);
    }

    /**
     * Removes the given value from the entry of the specified key.
     *
     * @param key   the key of the entry to remove from
     * @param value the value to remove from the entry
     * @return true if the value was removed
     */
    public boolean remove(String key, String value) {
        return h.remove(key, value);
    }

    /**
     * Convenience method for retrieving the first value of a named header field. If the header is not set, or if the
     * value list is empty, this method returns null.
     *
     * @param key the key whose first value to return.
     * @return the first value of the named header, or null.
     */
    public String getFirst(String key) {
        return h.getFirst(key);
    }

    /**
     * Convenience method for checking whether a named header field is <em>true</em>. To satisfy this, the
     * header field needs to have at least 1 entry, and Boolean.valueOf() of all its values must parse as
     * <em>true</em>.
     *
     * @param key the key whose values to parse as a boolean
     * @return the boolean value of the named header
     */
    public boolean isTrue(String key) {
        return h.isTrue(key);
    }

    /**
     * Returns an unmodifiable list of all key-value pairs of this. This provides a flattened view on the content of
     * this map.
     *
     * @return the collection of entries
     */
    public List<Entry<String, String>> entries() {
        return h.entries();
    }

}
