// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.language.process;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A list of strings which does not allow for duplicate elements.
 *
 * @author steinar
 */
public final class StemList extends AbstractList<String> {

    private final Optional<String> origin;
    private final List<String> stems;

    public StemList() {
        this(new String[0]);
    }

    public StemList(String... stems) {
        this(Optional.empty(), stems);
    }

    public StemList(Optional<String> origin, String... stems) {
        super();
        this.origin = origin;
        this.stems = new ArrayList<>(Math.max(stems.length, 3));
        for (String word : stems) {
            add(word);
        }
    }

    public Optional<String> getOrigin() { return origin; }

    @Override
    public String get(int i) {
        return stems.get(i);
    }

    @Override
    public int size() {
        return stems.size();
    }

    @Override
    public String set(int i, String element) {
        int existing = stems.indexOf(element);
        if (existing >= 0 && existing != i) {
            // the element already exists
            return element;
        } else {
            return stems.set(i, element);
        }
    }

    @Override
    public void add(int i, String element) {
        int existing = stems.indexOf(element);
        if (existing < 0) {
            stems.add(i, element);
        }
    }

    @Override
    public String remove(int i) {
        return stems.remove(i);
    }

}
