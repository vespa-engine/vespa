// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.result;

import com.yahoo.collections.LazyMap;
import com.yahoo.search.grouping.Continuation;
import com.yahoo.search.result.HitGroup;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Simon Thoresen
 */
public abstract class AbstractList extends HitGroup {

    private final Map<String, Continuation> continuations = LazyMap.newHashMap();
    private final String label;

    /**
     * <p>Constructs a new instance of this class.</p>
     *
     * @param type  The type of this list.
     * @param label The label of this list.
     */
    public AbstractList(String type, String label) {
        super(type + ":" + label);
        this.label = label;
    }

    /**
     * <p>Returns the label of this list.</p>
     *
     * @return The label.
     */
    public String getLabel() {
        return label;
    }

    /**
     * <p>Returns the map of all possible {@link Continuation}s of this list.</p>
     *
     * @return The list of Continuations.
     */
    public Map<String, Continuation> continuations() {
        return continuations;
    }

    @Override
    public void close() {
        super.close();
        continuations.clear();
    }

}
