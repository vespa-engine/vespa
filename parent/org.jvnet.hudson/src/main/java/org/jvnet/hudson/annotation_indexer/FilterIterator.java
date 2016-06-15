// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/*
 * The contents of this file are subject to the terms of the Common Development
 * and Distribution License (the License). You may not use this file except in
 * compliance with the License.
 *
 * You can obtain a copy of the License at http://www.sun.com/cddl/cddl.html
 * or http://www.netbeans.org/cddl.txt.
 *
 * When distributing Covered Code, include this CDDL Header Notice in each file
 * and include the License file at http://www.netbeans.org/cddl.txt.
 * If applicable, add the following below the CDDL Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 *
 * The Original Software is SezPoz. The Initial Developer of the Original
 * Software is Sun Microsystems, Inc. Portions Copyright 2008 Sun
 * Microsystems, Inc. All Rights Reserved.
 */package org.jvnet.hudson.annotation_indexer;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * @author Kohsuke Kawaguchi
 */
abstract class FilterIterator<T> implements Iterator<T> {
    private final Iterator<? extends T> core;
    private T next;
    private boolean fetched;

    protected FilterIterator(Iterator<? extends T> core) {
        this.core = core;
    }

    protected FilterIterator(Iterable<? extends T> core) {
        this(core.iterator());
    }

    private void fetch() {
        while(!fetched && core.hasNext()) {
            T n = core.next();
            if(filter(n)) {
                next = n;
                fetched = true;
            }
        }
    }

    /**
     * Filter out items in the original collection.
     *
     * @return
     *      true to leave this item and return this item from this iterator.
     *      false to hide this item.
     */
    protected abstract boolean filter(T t);

    public boolean hasNext() {
        fetch();
        return fetched;
    }

    public T next() {
        fetch();
        if(!fetched)  throw new NoSuchElementException();
        fetched = false;
        return next;
    }

    public void remove() {
        core.remove();
    }
}
