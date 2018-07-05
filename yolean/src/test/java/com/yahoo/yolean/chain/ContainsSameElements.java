// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.yolean.chain;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

import static java.util.Collections.sort;

/**
 * @author Tony Vaagenes
 */
class ContainsSameElements<T> extends TypeSafeMatcher<Collection<? super T>> {

    private final Set<T> identitySet;

    public static <T> Matcher<Collection<? super T>> containsSameElements(Collection<T> collection) {
        return new ContainsSameElements<>(collection);
    }

    public ContainsSameElements(Collection<T> collection) {
        identitySet = toIdentitySet(collection);
    }

    @SuppressWarnings("SuspiciousMethodCalls")
    @Override
    protected boolean matchesSafely(Collection<? super T> collection2) {
        for (Object elem : collection2) {
            if (!identitySet.contains(elem)) {
                return false;
            }
        }

        return collection2.size() == identitySet.size();
    }

    @Override
    public void describeTo(Description description) {
        description.appendText("containsSameElements ");
        appendCollection(description, identitySet);
    }

    private void appendCollection(Description description, Collection<?> collection) {
        description.appendValueList("{", ", ", "}", elementsToStringSorted(collection));
    }

    private List<String> elementsToStringSorted(Collection<?> collection) {
        List<String> result = new ArrayList<>();
        for (Object o : collection) {
            result.add(o.toString());
        }
        sort(result);
        return result;
    }

    @Override
    protected void describeMismatchSafely(Collection<? super T> collection, Description description) {
        description.appendText("was ");
        appendCollection(description, collection);
    }

    public static <T> Set<T> toIdentitySet(Collection<? extends T> collection) {
        Set<T> identitySet = Collections.newSetFromMap(new IdentityHashMap<T, Boolean>());
        identitySet.addAll(collection);
        return identitySet;
    }
}
