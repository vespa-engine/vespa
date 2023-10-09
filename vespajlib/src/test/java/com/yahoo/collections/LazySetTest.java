// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.collections;

import org.junit.Test;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author Simon Thoresen Hult
 */
public class LazySetTest {

    @Test
    public void requireThatInitialDelegateIsEmpty() {
        LazySet<String> set = newLazySet(new HashSet<String>());
        assertEquals(LazySet.EmptySet.class, set.getDelegate().getClass());
    }

    @Test
    public void requireThatEmptySetAddUpgradesToSingletonSet() {
        LazySet<String> set = newLazySet(new HashSet<String>());
        assertTrue(set.add("foo"));
        assertEquals(LazySet.SingletonSet.class, set.getDelegate().getClass());

        set = newLazySet(new HashSet<String>());
        assertTrue(set.addAll(Arrays.asList("foo")));
        assertEquals(LazySet.SingletonSet.class, set.getDelegate().getClass());
    }

    @Test
    public void requireThatEmptySetAddAllEmptySetDoesNotUpgradeToSingletonSet() {
        LazySet<String> set = newLazySet(new HashSet<String>());
        assertFalse(set.addAll(Collections.<String>emptySet()));
        assertEquals(LazySet.EmptySet.class, set.getDelegate().getClass());
    }

    @Test
    public void requireThatEmptySetAddAllUpgradesToFinalSet() {
        Set<String> delegate = new HashSet<>();
        LazySet<String> set = newLazySet(delegate);
        assertTrue(set.addAll(Arrays.asList("foo", "bar")));
        assertSame(delegate, set.getDelegate());
        assertEquals(2, delegate.size());
        assertTrue(delegate.contains("foo"));
        assertTrue(delegate.contains("bar"));
    }

    @Test
    public void requireThatSingletonSetRemoveEntryDowngradesToEmptySet() {
        LazySet<String> set = newSingletonSet("foo");
        assertTrue(set.remove("foo"));
        assertEquals(LazySet.EmptySet.class, set.getDelegate().getClass());
    }

    @Test
    public void requireThatSingletonSetRemoveUnknownDoesNotDowngradesToEmptySet() {
        LazySet<String> set = newSingletonSet("foo");
        assertFalse(set.remove("bar"));
        assertEquals(LazySet.SingletonSet.class, set.getDelegate().getClass());
    }

    @Test
    public void requireThatSingletonSetAddAllEmptySetDoesNotUpgradeToFinalSet() {
        LazySet<String> set = newSingletonSet("foo");
        assertFalse(set.addAll(Collections.<String>emptySet()));
        assertEquals(LazySet.SingletonSet.class, set.getDelegate().getClass());
    }

    @Test
    public void requireThatSingletonSetAddKnownDoesNotUpgradeToFinalSet() {
        LazySet<String> set = newSingletonSet("foo");
        assertFalse(set.add("foo"));
        assertEquals(LazySet.SingletonSet.class, set.getDelegate().getClass());
    }

    @Test
    public void requireThatSingletonSetAddUpgradesToFinalSet() {
        Set<String> delegate = new HashSet<>();
        LazySet<String> set = newSingletonSet(delegate, "foo");
        assertTrue(set.add("bar"));
        assertSame(delegate, set.getDelegate());
        assertEquals(2, delegate.size());
        assertTrue(delegate.contains("foo"));
        assertTrue(delegate.contains("bar"));
    }

    @Test
    public void requireThatSingletonSetAddAllUpgradesToFinalSet() {
        Set<String> delegate = new HashSet<>();
        LazySet<String> set = newSingletonSet(delegate, "foo");
        assertTrue(set.addAll(Arrays.asList("bar")));
        assertSame(delegate, set.getDelegate());
        assertEquals(2, delegate.size());
        assertTrue(delegate.contains("foo"));
        assertTrue(delegate.contains("bar"));

        delegate = new HashSet<>();
        set = newSingletonSet(delegate, "foo");
        assertTrue(set.addAll(Arrays.asList("bar", "baz")));
        assertSame(delegate, set.getDelegate());
        assertEquals(3, delegate.size());
        assertTrue(delegate.contains("foo"));
        assertTrue(delegate.contains("bar"));
        assertTrue(delegate.contains("baz"));
    }

    @Test
    public void requireThatSingletonIteratorNextThrowsIfInvokedMoreThanOnce() {
        LazySet<String> set = newSingletonSet("foo");
        Iterator<String> it = set.iterator();
        it.next();
        try {
            it.next();
            fail();
        } catch (NoSuchElementException e) {

        }
        try {
            it.next();
            fail();
        } catch (NoSuchElementException e) {

        }
    }

    @Test
    public void requireThatSingletonIteratorRemoveDowngradesToEmptySet() {
        LazySet<String> set = newSingletonSet("foo");
        Iterator<String> it = set.iterator();
        it.next();
        it.remove();
        assertEquals(LazySet.EmptySet.class, set.getDelegate().getClass());
    }

    @Test
    public void requireThatSingletonIteratorRemoveThrowsIfInvokedBeforeNext() {
        LazySet<String> set = newSingletonSet("foo");
        Iterator<String> it = set.iterator();
        try {
            it.remove();
            fail();
        } catch (IllegalStateException e) {

        }
    }

    @SuppressWarnings("unchecked")
    private static Set<String> makeMockSet() {
        return Mockito.mock(Set.class);
    }

    @Test
    public void requireThatSetDelegates() {
        Set<String> delegate = makeMockSet();
        Set<String> set = newLazySet(delegate);
        set.add("foo");
        set.add("bar"); // trigger the assignment of the delegate
        Mockito.verify(delegate).add("foo");
        Mockito.verify(delegate).add("bar");

        Set<String> addAllArg = Collections.singleton("foo");
        set.addAll(addAllArg);
        Mockito.verify(delegate).addAll(addAllArg);

        assertEquals(0, set.size());
        Mockito.verify(delegate).size();

        assertFalse(set.isEmpty());
        Mockito.verify(delegate).isEmpty();

        assertFalse(set.contains("foo"));
        Mockito.verify(delegate).contains("foo");

        assertNull(set.iterator());
        Mockito.verify(delegate).iterator();

        assertNull(set.toArray());
        Mockito.verify(delegate).toArray();

        String[] toArrayArg = new String[69];
        assertNull(set.toArray(toArrayArg));
        Mockito.verify(delegate).toArray(toArrayArg);

        assertFalse(set.remove("foo"));
        Mockito.verify(delegate).remove("foo");

        Collection<String> containsAllArg = Collections.singletonList("foo");
        assertFalse(set.containsAll(containsAllArg));
        Mockito.verify(delegate).containsAll(containsAllArg);

        Collection<String> retainAllArg = Collections.singletonList("foo");
        assertFalse(set.retainAll(retainAllArg));
        Mockito.verify(delegate).retainAll(retainAllArg);

        Collection<String> removeAllArg = Collections.singletonList("foo");
        assertFalse(set.removeAll(removeAllArg));
        Mockito.verify(delegate).removeAll(removeAllArg);

        set.clear();
        Mockito.verify(delegate).clear();
    }

    @Test
    public void requireThatHashCodeIsImplemented() {
        assertEquals(newLazySet(null).hashCode(),
                     newLazySet(null).hashCode());
    }

    @Test
    public void requireThatEqualsIsImplemented() {
        Set<Object> lhs = newLazySet(new HashSet<>());
        Set<Object> rhs = newLazySet(new HashSet<>());
        assertEquals(lhs, lhs);
        assertEquals(lhs, rhs);

        Object obj = new Object();
        lhs.add(obj);
        assertEquals(lhs, lhs);
        assertFalse(lhs.equals(rhs));
        rhs.add(obj);
        assertEquals(lhs, rhs);
    }

    @Test
    public void requireThatHashSetFactoryDelegatesToAHashSet() {
        LazySet<Integer> set = LazySet.newHashSet();
        set.add(6);
        set.add(9);
        assertEquals(HashSet.class, set.getDelegate().getClass());
    }

    private static <E> LazySet<E> newSingletonSet(E element) {
        return newSingletonSet(new HashSet<E>(), element);
    }

    private static <E> LazySet<E> newSingletonSet(Set<E> delegate, E element) {
        LazySet<E> set = newLazySet(delegate);
        set.add(element);
        return set;
    }

    private static <E> LazySet<E> newLazySet(final Set<E> delegate) {
        return new LazySet<E>() {

            @Override
            protected Set<E> newDelegate() {
                return delegate;
            }
        };
    }
}
