// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.collections;

import java.util.Collection;
import java.util.List;

/**
 * An array list which can be frozen to disallow further edits.
 * After freezing, edit operations will throw UnsupportedOperationException.
 * Freezable lists may optionally allow new items to be added to the end of the list also after freeze.
 *
 * @author bratseth
 */
public class FreezableArrayList<ITEM> extends ListenableArrayList<ITEM> {

    private static final long serialVersionUID = 5900452593651895638L;

    private final boolean permitAddAfterFreeze;
    private boolean frozen = false;

    /** Creates a freezable array list which does not permit adds after freeze */
    public FreezableArrayList() {
        this(false);
    }

    /** Creates a freezable array list which does not permit adds after freeze */
    public FreezableArrayList(int initialCapacity) {
        this(false, initialCapacity);
    }

    public FreezableArrayList(boolean permitAddAfterFreeze) {
        this.permitAddAfterFreeze = permitAddAfterFreeze;
    }

    public FreezableArrayList(boolean permitAddAfterFreeze, int initialCapacity) {
        super(initialCapacity);
        this.permitAddAfterFreeze = permitAddAfterFreeze;
    }

    /** Irreversibly freezes the content of this */
    public void freeze() {
        this.frozen = true;
    }

    @Override
    public boolean add(ITEM e) {
        if ( ! permitAddAfterFreeze) throwIfFrozen();
        return super.add(e);
    }

    @Override
    public void add(int index, ITEM e) {
        throwIfFrozen();
        super.add(index, e);
    }

    @Override
    public boolean addAll(Collection<? extends ITEM> a) {
        if ( ! permitAddAfterFreeze) throwIfFrozen();
        return super.addAll(a);
    }

    @Override
    public boolean addAll(int index, Collection<? extends ITEM> a) {
        throwIfFrozen();
        return super.addAll(index, a);
    }

    @Override
    public ITEM set(int index, ITEM e) {
        throwIfFrozen();
        return super.set(index, e);
    }

    @Override
    public ITEM remove(int index) {
        throwIfFrozen();
        return super.remove(index);
    }

    @Override
    public boolean remove(Object o) {
        throwIfFrozen();
        return super.remove(o);
    }

    @Override
    public void clear() {
        throwIfFrozen();
        super.clear();
    }

    @Override
    protected void removeRange(int fromIndex, int toIndex) {
        throwIfFrozen();
        super.removeRange(fromIndex, toIndex);
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        throwIfFrozen();
        return super.removeAll(c);
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        throwIfFrozen();
        return super.retainAll(c);
    }

    private void throwIfFrozen() {
        if ( frozen )
            throw new UnsupportedOperationException(this + " is frozen");
    }

}
