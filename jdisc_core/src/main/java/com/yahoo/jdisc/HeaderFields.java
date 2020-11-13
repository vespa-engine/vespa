// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc;

import com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * This is an encapsulation of the header fields that belong to either a {@link Request} or a {@link Response}. It is
 * a multimap from String to String, with some additional methods for convenience. The keys of this map are compared by
 * ignoring their case, so that <code>get("foo")</code> returns the same entry as <code>get("FOO")</code>.
 *
 * @author Simon Thoresen Hult
 */
public class HeaderFields implements Map<String, List<String>> {

    private final ConcurrentSkipListMap<String, List<String>> content = new ConcurrentSkipListMap<>(String::compareToIgnoreCase);

    @Override
    public int size() {
        return content.size();
    }

    @Override
    public boolean isEmpty() {
        return content.isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        return content.containsKey(key);
    }

    @Override
    public boolean containsValue(Object value) {
        return content.containsValue(value);
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
        List<String> lst = content.get(key);
        if (lst == null) {
            return false;
        }
        return lst.contains(value);
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
        List<String> lst = content.get(key);
        if (lst == null) {
            return false;
        }
        for (String val : lst) {
            if (value.equalsIgnoreCase(val)) {
                return true;
            }
        }
        return false;
    }

    /**
     * <p>Adds the given value to the entry of the specified key. If no entry exists for the given key, a new one is
     * created containing only the given value.</p>
     *
     * @param key   The key with which the specified value is to be associated.
     * @param value The value to be added to the list associated with the specified key.
     */
    public void add(String key, String value) {
        List<String> lst = content.get(key);
        if (lst != null) {
            lst.add(value);
        } else {
            put(key, value);
        }
    }

    /**
     * <p>Adds the given values to the entry of the specified key. If no entry exists for the given key, a new one is
     * created containing only the given values.</p>
     *
     * @param key    The key with which the specified value is to be associated.
     * @param values The values to be added to the list associated with the specified key.
     */
    public void add(String key, List<String> values) {
        List<String> lst = content.get(key);
        if (lst != null) {
            lst.addAll(values);
        } else {
            put(key, values);
        }
    }

    /**
     * <p>Adds all the entries of the given map to this. This is the same as calling {@link #add(String, List)} for each
     * entry in <code>values</code>.</p>
     *
     * @param values The values to be added to this.
     */
    public void addAll(Map<? extends String, ? extends List<String>> values) {
        for (Entry<? extends String, ? extends List<String>> entry : values.entrySet()) {
            add(entry.getKey(), entry.getValue());
        }
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
        List<String> list = Collections.synchronizedList(new ArrayList<>(1));
        list.add(value);
        return content.put(key, list);
    }

    @Override
    public List<String> put(String key, List<String> value) {
        return content.put(key, Collections.synchronizedList(new ArrayList<>(value)));
    }

    @Override
    public void putAll(Map<? extends String, ? extends List<String>> values) {
        for (Entry<? extends String, ? extends List<String>> entry : values.entrySet()) {
            put(entry.getKey(), entry.getValue());
        }
    }

    @Override
    public List<String> remove(Object key) {
        return content.remove(key);
    }

    /**
     * <p>Removes the given value from the entry of the specified key.</p>
     *
     * @param key   The key of the entry to remove from.
     * @param value The value to remove from the entry.
     * @return True if the value was removed.
     */
    public boolean remove(String key, String value) {
        List<String> lst = content.get(key);
        if (lst == null) {
            return false;
        }
        if (!lst.remove(value)) {
            return false;
        }
        if (lst.isEmpty()) {
            content.remove(key);
        }
        return true;
    }

    @Override
    public void clear() {
        content.clear();
    }

    @Override
    public List<String> get(Object key) {
        return content.get(key);
    }

    /**
     * <p>Convenience method for retrieving the first value of a named header field. If the header is not set, or if the
     * value list is empty, this method returns null.</p>
     *
     * @param key The key whose first value to return.
     * @return The first value of the named header, or null.
     */
    public String getFirst(String key) {
        List<String> lst = get(key);
        if (lst == null || lst.isEmpty()) {
            return null;
        }
        return lst.get(0);
    }

    /**
     * <p>Convenience method for checking whether or not a named header field is <em>true</em>. To satisfy this, the
     * header field needs to have at least 1 entry, and Boolean.valueOf() of all its values must parse as
     * <em>true</em>.</p>
     *
     * @param key The key whose values to parse as a boolean.
     * @return The boolean value of the named header.
     */
    public boolean isTrue(String key) {
        List<String> lst = content.get(key);
        if (lst == null) {
            return false;
        }
        for (String value : lst) {
            if (!Boolean.valueOf(value)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public Set<String> keySet() {
        return content.keySet();
    }

    @Override
    public Collection<List<String>> values() {
        return content.values();
    }

    @Override
    public Set<Entry<String, List<String>>> entrySet() {
        return content.entrySet();
    }

    @Override
    public String toString() {
        return content.toString();
    }

    /**
     * <p>Returns an unmodifiable list of all key-value pairs of this. This provides a flattened view on the content of
     * this map.</p>
     *
     * @return The collection of entries.
     */
    public List<Entry<String, String>> entries() {
        List<Entry<String, String>> list = new ArrayList<>(content.size());
        for (Entry<String, List<String>> entry : content.entrySet()) {
            String key = entry.getKey();
            for (String value : entry.getValue()) {
                list.add(new MyEntry(key, value));
            }
        }
        return ImmutableList.copyOf(list);
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof HeaderFields && content.equals(((HeaderFields)obj).content);
    }

    @Override
    public int hashCode() {
        return content.hashCode();
    }

    private static class MyEntry implements Map.Entry<String, String> {

        final String key;
        final String value;

        private MyEntry(String key, String value) {
            this.key = key;
            this.value = value;
        }

        @Override
        public String getKey() {
            return key;
        }

        @Override
        public String getValue() {
            return value;
        }

        @Override
        public String setValue(String value) {
            throw new UnsupportedOperationException();
        }
    }

}
