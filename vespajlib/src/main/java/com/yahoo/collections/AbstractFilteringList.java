// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.collections;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.Spliterator;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;

/**
 * Abstract, immutable list for subclassing with concrete types and domain specific filters.
 *
 * @author jonmv
 */
public abstract class AbstractFilteringList<Type, ListType extends AbstractFilteringList<Type, ListType>> implements Iterable<Type> {

    private final List<Type> items;
    private final boolean negate;
    private final BiFunction<List<Type>, Boolean, ListType> constructor;

    // Subclass constructor:
    // private SomeFilteringList(Collection<? extends SomeType> items, boolean negate) {
    //     super(items, negate, SomeFilteringList::new);
    // }
    protected AbstractFilteringList(Collection<? extends Type> items, boolean negate, BiFunction<List<Type>, Boolean, ListType> constructor) {
        this.items = List.copyOf(items);
        this.negate = negate;
        this.constructor = constructor;
    }

    /** Negates the next filter operation. All other operations which return a new list reset this modifier. */
    public final ListType not() {
        return constructor.apply(items, ! negate);
    }

    /** Returns a new list which is the result of filtering with the -- possibly negated -- condition. */
    public final ListType matching(Predicate<Type> condition) {
        return constructor.apply(items.stream().filter(negate ? condition.negate() : condition).toList(), false);
    }

    /** Returns the first n items in this list, or everything except those if negated. */
    public ListType first(int n) {
        n = Math.min(n, items.size());
        return constructor.apply(items.subList(negate ? n : 0, negate ? items.size() : n), false);
    }

    /** Returns the first item in this list, or empty if there are none. */
    public Optional<Type> first() {
        return items.stream().findFirst();
    }

    /** Returns the subset of items in this which are (not) present in the other list. */
    public ListType in(ListType others) {
        return matching(new HashSet<>(others.asList())::contains);
    }

    /** Returns the union of the two lists. */
    public ListType and(ListType others) {
        return constructor.apply(Stream.concat(items.stream(), others.asList().stream()).toList(), false);
    }

    /** Returns the items in this as an immutable list. */
    public final List<Type> asList() { return items; }

    /** Returns the items in this as a set. */
    public final Set<Type> asSet() { return new HashSet<>(items); }

    /** Returns the items in this as an immutable list after mapping with the given function. */
    public final <OtherType> List<OtherType> mapToList(Function<Type, OtherType> mapper) {
        return items.stream().map(mapper).toList();
    }

    /** Returns the items sorted by the given comparator. */
    public final ListType sortedBy(Comparator<? super Type> comparator) {
        return constructor.apply(items.stream().sorted(comparator).toList(), false);
    }

    /** Returns the items grouped by the given classifier. */
    public final <OtherType> Map<OtherType, ListType> groupingBy(Function<Type, OtherType> classifier) {
        return items.stream().collect(Collectors.groupingBy(classifier,
                                                            LinkedHashMap::new,
                                                            Collectors.collectingAndThen(toList(),
                                                                                         (list) -> constructor.apply(list, false))));
    }

    public final boolean isEmpty() { return items.isEmpty(); }

    public final int size() { return items.size(); }

    @Override
    public Iterator<Type> iterator() {
        return items.iterator();
    }

    @Override
    public Spliterator<Type> spliterator() {
        return items.spliterator();
    }

    /** Returns the items in this shuffled using random as source of randomness */
    public final ListType shuffle(Random random) {
        ArrayList<Type> shuffled = new ArrayList<>(items);
        Collections.shuffle(shuffled, random);
        return constructor.apply(shuffled, false);
    }

}
