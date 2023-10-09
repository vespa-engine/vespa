// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/*
 * This is adapted from the Lucene code base which is Copyright 2008 Apache Software Foundation and Licensed
 * under the terms of the Apache License, Version 2.0.
 */
package com.yahoo.language.simple.kstem;


import java.util.AbstractSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.Set;

/**
 * A simple class that stores Strings as char[]'s in a
 * hash table.  Note that this is not a general purpose
 * class.  For example, it cannot remove items from the
 * set, nor does it resize its hash table to be smaller,
 * etc.  It is designed to be quick to test if a char[]
 * is in the set without the necessity of converting it
 * to a String first.
 *
 * <P>
 * <em>Please note:</em> This class implements {@link java.util.Set Set} but
 * does not behave like it should in all cases. The generic type is
 * {@code Set<Object>}, because you can add any object to it,
 * that has a string representation. The add methods will use
 * {@link Object#toString} and store the result using a {@code char[]}
 * buffer. The same behavior have the {@code contains()} methods.
 * The {@link #iterator()} returns an {@code Iterator<char[]>}.
 */
public class CharArraySet extends AbstractSet<Object> {

  public static final CharArraySet EMPTY_SET = new CharArraySet(CharArrayMap.<Object>emptyMap());
  private static final Object PLACEHOLDER = new Object();
  
  private final CharArrayMap<Object> map;
  
  /**
   * Create set with enough capacity to hold startSize terms
   * 
   * @param startSize
   *          the initial capacity
   * @param ignoreCase
   *          <code>false</code> if and only if the set should be case sensitive
   *          otherwise <code>true</code>.
   */
  public CharArraySet(int startSize, boolean ignoreCase) {
    this(new CharArrayMap<>(startSize, ignoreCase));
  }

  /**
   * Creates a set from a Collection of objects. 
   * 
   * @param c
   *          a collection whose elements to be placed into the set
   * @param ignoreCase
   *          <code>false</code> if and only if the set should be case sensitive
   *          otherwise <code>true</code>.
   */
  public CharArraySet(Collection<?> c, boolean ignoreCase) {
    this(c.size(), ignoreCase);
    addAll(c);
  }

  /** Create set from the specified map (internal only), used also by {@link CharArrayMap#keySet()} */
  CharArraySet(final CharArrayMap<Object> map){
    this.map = map;
  }
  
  /** Clears all entries in this set. This method is supported for reusing, but not {@link Set#remove}. */
  @Override
  public void clear() {
    map.clear();
  }

  /** true if the <code>len</code> chars of <code>text</code> starting at <code>off</code>
   * are in the set */
  public boolean contains(char[] text, int off, int len) {
    return map.containsKey(text, off, len);
  }

  /** true if the <code>CharSequence</code> is in the set */
  public boolean contains(CharSequence cs) {
    return map.containsKey(cs);
  }

  @Override
  public boolean contains(Object o) {
    return map.containsKey(o);
  }

  @Override
  public boolean add(Object o) {
    return map.put(o, PLACEHOLDER) == null;
  }

  /** Add this CharSequence into the set */
  public boolean add(CharSequence text) {
    return map.put(text, PLACEHOLDER) == null;
  }
  
  /** Add this String into the set */
  public boolean add(String text) {
    return map.put(text, PLACEHOLDER) == null;
  }

  /** Add this char[] directly to the set.
   * If ignoreCase is true for this Set, the text array will be directly modified.
   * The user should never modify this text array after calling this method.
   */
  public boolean add(char[] text) {
    return map.put(text, PLACEHOLDER) == null;
  }

  @Override
  public int size() {
    return map.size();
  }
  
  /**
   * Returns an unmodifiable {@link CharArraySet}. This allows to provide
   * unmodifiable views of internal sets for "read-only" use.
   * 
   * @param set
   *          a set for which the unmodifiable set is returned.
   * @return an new unmodifiable {@link CharArraySet}.
   * @throws NullPointerException
   *           if the given set is <code>null</code>.
   */
  public static CharArraySet unmodifiableSet(CharArraySet set) {
    if (set == null)
      throw new NullPointerException("Given set is null");
    if (set == EMPTY_SET)
      return EMPTY_SET;
    if (set.map instanceof CharArrayMap.UnmodifiableCharArrayMap)
      return set;
    return new CharArraySet(CharArrayMap.unmodifiableMap(set.map));
  }

  /**
   * Returns a copy of the given set as a {@link CharArraySet}. If the given set
   * is a {@link CharArraySet} the ignoreCase property will be preserved.
   * 
   * @param set
   *          a set to copy
   * @return a copy of the given set as a {@link CharArraySet}. If the given set
   *         is a {@link CharArraySet} the ignoreCase property as well as the
   *         matchVersion will be of the given set will be preserved.
   */
  public static CharArraySet copy(final Set<?> set) {
    if(set == EMPTY_SET)
      return EMPTY_SET;
    if(set instanceof CharArraySet) {
      final CharArraySet source = (CharArraySet) set;
      return new CharArraySet(CharArrayMap.copy(source.map));
    }
    return new CharArraySet(set, false);
  }
  
  /**
   * Returns an {@link Iterator} for {@code char[]} instances in this set.
   */
  @Override @SuppressWarnings("unchecked")
  public Iterator<Object> iterator() {
    // use the AbstractSet#keySet()'s iterator (to not produce endless recursion)
    return map.originalKeySet().iterator();
  }
  
  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder("[");
    for (Object item : this) {
      if (sb.length()>1) sb.append(", ");
      if (item instanceof char[]) {
        sb.append((char[]) item);
      } else {
        sb.append(item);
      }
    }
    return sb.append(']').toString();
  }

}
