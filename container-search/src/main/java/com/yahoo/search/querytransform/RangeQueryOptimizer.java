// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.querytransform;

import com.yahoo.prelude.query.Limit;
import com.yahoo.prelude.IndexFacts;
import com.yahoo.prelude.query.AndItem;
import com.yahoo.prelude.query.CompositeItem;
import com.yahoo.prelude.query.FalseItem;
import com.yahoo.prelude.query.IntItem;
import com.yahoo.prelude.query.Item;
import com.yahoo.prelude.query.QueryCanonicalizer;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.search.searchchain.PhaseNames;
import com.yahoo.yolean.chain.After;
import com.yahoo.yolean.chain.Before;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

/**
 * Finds and optimizes ranges in queries:
 * For single value attributes c1 $lt; x AND x &gt; c2  becomes  x IN &lt;c1; c2&gt;.
 * The query cost saving from this has been shown to be 2 orders of magnitude in real cases.
 *
 * @author bratseth
 */
@Before(QueryCanonicalizer.queryCanonicalization)
@After(PhaseNames.TRANSFORMED_QUERY)
public class RangeQueryOptimizer extends Searcher {

    @Override
    public Result search(Query query, Execution execution) {
        if (execution.context().getIndexFacts() == null) return execution.search(query); // this is a test query

        boolean optimized = recursiveOptimize(query.getModel().getQueryTree(), execution.context().getIndexFacts().newSession(query));
        if (optimized)
            query.trace("Optimized query ranges", true, 2);
        return execution.search(query);
    }

    /** Recursively performs the range optimization on this query tree and returns whether at least one optimization was done */
    private boolean recursiveOptimize(Item item, IndexFacts.Session indexFacts) {
        if ( ! (item instanceof CompositeItem)) return false;

        boolean optimized = false;
        for (Iterator<Item> i = ((CompositeItem) item).getItemIterator(); i.hasNext(); )
            optimized |= recursiveOptimize(i.next(), indexFacts);

        if (item instanceof AndItem)
            optimized |= optimizeAnd((AndItem)item, indexFacts);
        return optimized;
    }

    private boolean optimizeAnd(AndItem and, IndexFacts.Session indexFacts) {
        // Find consolidated ranges by collecting a list of compatible ranges
        List<FieldRange> fieldRanges = null;
        for (Iterator<Item> i = and.getItemIterator(); i.hasNext(); ) {
            Item item = i.next();
            if ( ! (item instanceof IntItem intItem)) continue;
            if (intItem.getHitLimit() != 0) continue; // each such op gets a different partial set: Cannot be optimized
            if (intItem.getFromLimit().equals(intItem.getToLimit())) continue; // don't optimize searches for single numbers
            if (indexFacts.getIndex(intItem.getIndexName()).isMultivalue()) continue; // May match different values in each range

            if (fieldRanges == null) fieldRanges = new ArrayList<>();
            Optional<FieldRange> compatibleRange = findCompatibleRange(intItem, fieldRanges);
            if (compatibleRange.isPresent())
                compatibleRange.get().addRange(intItem);
            else
                fieldRanges.add(new FieldRange(intItem));
            i.remove();
        }

        // Add consolidated ranges
        if (fieldRanges == null) return false;

        boolean optimized = false;
        for (FieldRange fieldRange : fieldRanges) {
            and.addItem(fieldRange.toItem());
            optimized |= fieldRange.isOptimization();
        }
        return optimized;
    }

    private Optional<FieldRange> findCompatibleRange(IntItem item, List<FieldRange> fieldRanges) {
        for (FieldRange fieldRange : fieldRanges) {
            if (fieldRange.isCompatibleWith(item))
                return Optional.of(fieldRange);
        }
        return Optional.empty();
    }

    /** Represents the ranges searched in a single field */
    @SuppressWarnings("deprecation")
    private static final class FieldRange {

        private Range range = new Range(new Limit(Double.NEGATIVE_INFINITY, false), new Limit(Double.POSITIVE_INFINITY, false));
        private int sourceRangeCount = 0;

        // IntItem fields which must be preserved in the produced item.
        // This is an unfortunate coupling and ideally we should delegate this (creation, compatibility)
        // to the Item classes
        private final String indexName;
        private final Item.ItemCreator creator;
        private final boolean ranked;
        private final int weight;

        public FieldRange(IntItem item) {
            this.indexName = item.getIndexName();
            this.creator = item.getCreator();
            this.ranked = item.isRanked();
            this.weight = item.getWeight();
            addRange(item);
        }

        public String getIndexName() { return indexName; }

        public boolean isCompatibleWith(IntItem item) {
            if ( ! indexName.equals(item.getIndexName())) return false;
            if (creator != item.getCreator()) return false;
            if (ranked != item.isRanked()) return false;
            if (weight != item.getWeight()) return false;
            return true;
        }

        /** Adds a range for this field */
        public void addRange(IntItem item) {
            range = range.intersection(new Range(item));
            sourceRangeCount++;
        }

        public Item toItem() {
            Item item = range.toItem(indexName);
            item.setCreator(creator);
            item.setRanked(ranked);
            item.setWeight(weight);
            return item;
        }

        /** Returns whether this range is actually an optimization over what was in the source query */
        public boolean isOptimization() { return sourceRangeCount > 1; }

    }

    /** An immutable numerical range */
    private static class Range {

        private final Limit from;
        private final Limit to;

        private static final Range empty = new EmptyRange();

        public Range(Limit from, Limit to) {
            this.from = from;
            this.to = to;
        }

        public Range(IntItem range) {
            from = range.getFromLimit();
            to = range.getToLimit();
        }

        /** Returns true if these two ranges overlap */
        public boolean overlaps(Range other) {
            if (other.from.isSmallerOrEqualTo(this.to) && other.to.isLargerOrEqualTo(this.from)) return true;
            if (other.to.isLargerOrEqualTo(this.from) && other.from.isSmallerOrEqualTo(this.to)) return true;
            return false;
        }

        /**
         * Returns the intersection of this and the given range.
         * If the ranges does not overlap, an empty range is returned.
         */
        public Range intersection(Range other) {
            if ( ! overlaps(other)) return empty;
            return new Range(from.max(other.from), to.min(other.to));
        }

        public Item toItem(String fieldName) {
            return IntItem.from(fieldName, from, to, 0);
        }

        @Override
        public String toString() { return "[" + from + ";" + to + "]"; }

    }

    private static class EmptyRange extends Range {

        public EmptyRange() {
            super(new Limit(0, false), new Limit(0, false)); // the to and from of an empty range is never used.
        }

        @Override
        public boolean overlaps(Range other) { return false; }

        @Override
        public Range intersection(Range other) { return this; }

        @Override
        public Item toItem(String fieldName) { return new FalseItem(); }

        @Override
        public String toString() { return "(empty)"; }

    }

}
