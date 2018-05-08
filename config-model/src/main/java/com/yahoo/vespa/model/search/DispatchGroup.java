// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.search;

import com.yahoo.vespa.model.content.SearchCoverage;

import java.util.*;

/**
 * Class representing a group of @link{SearchInterface} nodes and a set of @link{Dispatch} nodes.
 *
 * Each @link{Dispatch} has a reference to an instance of this class and use it when producing config.
 *
 * @author baldersheim
 */
public class DispatchGroup {

    private final List<Dispatch> dispatchers = new ArrayList<>();
    private final Map<Integer, Map<Integer, SearchInterface> > searchers = new TreeMap<>();

    final private IndexedSearchCluster sc;

    public DispatchGroup(IndexedSearchCluster sc) {
        this.sc = sc;
    }

    DispatchGroup addDispatcher(Dispatch dispatch) {
        dispatchers.add(dispatch);
        return this;
    }

    DispatchGroup addSearcher(SearchInterface search) {
        Map<Integer, SearchInterface> rows = searchers.get(search.getNodeSpec().partitionId());
        if (rows == null) {
            rows = new TreeMap<>();
            rows.put(search.getNodeSpec().groupIndex(), search);
            searchers.put(search.getNodeSpec().partitionId(), rows);
        } else {
            if (rows.containsKey(search.getNodeSpec().groupIndex())) {
                throw new IllegalArgumentException("Already contains a search node with row id '" + search.getNodeSpec().groupIndex() + "'");
            }
            rows.put(search.getNodeSpec().groupIndex(), search);
        }
        return this;
    }

    DispatchGroup clearSearchers() {
        searchers.clear();
        return this;
    }

    List<Dispatch> getDispatchers() {
        return Collections.unmodifiableList(dispatchers);
    }

    public Iterable getSearchersIterable() {
        return new Iterable(searchers);
    }

    public int getRowBits() {
        return sc.getRowBits();
    }

    public int getNumPartitions() {
        return searchers.size();
    }

    public boolean useFixedRowInDispatch() {
        return sc.useFixedRowInDispatch();
    }

    public int getMinNodesPerColumn() {
        return sc.getMinNodesPerColumn();
    }

    public int getSearchableCopies() { return sc.getSearchableCopies(); }

    public int getMaxNodesDownPerFixedRow() {
        return sc.getMaxNodesDownPerFixedRow();
    }

    SearchCoverage getSearchCoverage() {
        return sc.getSearchCoverage();
    }

    Tuning getTuning() {
        return sc.getTuning();
    }

    String getClusterName() {
        return sc.getClusterName();
    }

    static class Iterator implements java.util.Iterator<SearchInterface> {
        private java.util.Iterator<Map<Integer, SearchInterface>> it1;
        private java.util.Iterator<SearchInterface> it2;
        Iterator(Map<Integer, Map<Integer, SearchInterface> > s) {
            it1 = s.values().iterator();
            if (it1.hasNext()) {
                it2 = it1.next().values().iterator();
            }
        }
        @Override
        public boolean hasNext() {
            if (it2 == null) {
                return false;
            }
            while (!it2.hasNext() && it1.hasNext()) {
                it2 = it1.next().values().iterator();
            }
            return it2.hasNext();
        }

        @Override
        public SearchInterface next() {
            return it2.next();
        }

        @Override
        public void remove() {
            throw new IllegalStateException("'remove' not implemented");
        }
    }

    public static class Iterable implements java.lang.Iterable<SearchInterface> {
        final Map<Integer, Map<Integer, SearchInterface> > searchers;
        Iterable(Map<Integer, Map<Integer, SearchInterface> > searchers) { this.searchers = searchers; }
        @Override
        public java.util.Iterator<SearchInterface> iterator() {
            return new Iterator(searchers);
        }
    }

}
