// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query;

import com.yahoo.processing.request.CompoundName;
import com.yahoo.search.Query;
import com.yahoo.search.query.QueryTree;
import com.yahoo.search.query.properties.DefaultProperties;

import java.util.HashSet;
import java.util.ListIterator;
import java.util.Optional;
import java.util.Set;

/**
 * Query normalizer and sanity checker.
 *
 * @author bratseth
 */
public class QueryCanonicalizer {

    /** The name of the operation performed by this, for use in search chain ordering */
    public static final String queryCanonicalization = "queryCanonicalization";

    /**
     * Validates this query and carries out possible operations on this query
     * which simplifies it without changing its semantics.
     *
     * @return null if the query is valid, an error message if it is invalid
     */
    public static String canonicalize(Query query) {
        return canonicalize(query.getModel().getQueryTree(),
                            query.properties().getInteger(DefaultProperties.MAX_QUERY_ITEMS));
    }

    /**
     * Canonicalizes this query, allowing any query tree size
     *
     * @return null if the query is valid, an error message if it is invalid
     */
    public static String canonicalize(QueryTree queryTree) {
        return canonicalize(queryTree, Integer.MAX_VALUE);
    }

    /**
     * Canonicalizes this query
     * 
     * @return null if the query is valid, an error message if it is invalid
     */
    private static String canonicalize(QueryTree query, Integer maxQueryItems) {
        ListIterator<Item> rootItemIterator = query.getItemIterator();
        CanonicalizationResult result = recursivelyCanonicalize(rootItemIterator.next(), rootItemIterator);
        if (query.isEmpty() && ! result.isError()) result = CanonicalizationResult.error("No query");
        int itemCount = query.treeSize();
        if (itemCount > maxQueryItems)
            result = CanonicalizationResult.error(String.format("Query tree exceeds allowed item count. Configured limit: %d - Item count: %d", maxQueryItems, itemCount));
        return result.error().orElse(null); // preserve old API, unfortunately
    }

    /**
     * Canonicalize this query
     * 
     * @param item the item to canonicalize
     * @param parentIterator iterator for the parent of this item, never null
     * @return true if the given query is valid, false otherwise
     */
    private static CanonicalizationResult recursivelyCanonicalize(Item item, ListIterator<Item> parentIterator) {
        // children first as they may be removed
        if (item instanceof CompositeItem) {
            CompositeItem composite = (CompositeItem)item;
            for (ListIterator<Item> i = composite.getItemIterator(); i.hasNext(); ) {
                CanonicalizationResult childResult = recursivelyCanonicalize(i.next(), i);
                if (childResult.isError()) return childResult;
            }
        }

        return canonicalizeThis(item, parentIterator);
    }
    
    private static CanonicalizationResult canonicalizeThis(Item item, ListIterator<Item> parentIterator) {
        if (item instanceof NullItem) parentIterator.remove();
        if ( ! (item instanceof CompositeItem)) return CanonicalizationResult.success();
        CompositeItem composite = (CompositeItem)item;

        boolean replacedByFalse = collapseFalse(composite, parentIterator);
        if (replacedByFalse) return CanonicalizationResult.success();

        collapseLevels(composite);

        if (composite instanceof EquivItem) {
            removeDuplicates((EquivItem) composite);
        }
        else if (composite instanceof RankItem) {
            makeDuplicatesCheap((RankItem)composite);
        }
        if (composite.getItemCount() == 0)
            parentIterator.remove();

        composite.extractSingleChild().ifPresent(extractedChild -> parentIterator.set(extractedChild));

        return CanonicalizationResult.success();
    }

    private static void collapseLevels(CompositeItem composite) {
        if (composite instanceof RankItem || composite instanceof NotItem) {
            collapseLevels(composite, composite.getItemIterator()); // collapse the first item only
        }
        else if (composite instanceof AndItem || composite instanceof OrItem || composite instanceof WeakAndItem) {
            for (ListIterator<Item> i = composite.getItemIterator(); i.hasNext(); )
                collapseLevels(composite, i);
        }
    }
    
    /** Collapse the next item of this iterator into the given parent, if appropriate */
    private static void collapseLevels(CompositeItem composite, ListIterator<Item> i) {
        if ( ! i.hasNext()) return;
        Item child = i.next();
        if (child == null) return;
        if (child.getClass() != composite.getClass()) return;
        if (child instanceof WeakAndItem && !equalWeakAndSettings((WeakAndItem)child, (WeakAndItem)composite)) return;
        i.remove();
        moveChildren((CompositeItem) child, i);
    }

    private static boolean equalWeakAndSettings(WeakAndItem a, WeakAndItem b) {
        if ( ! a.getIndexName().equals(b.getIndexName())) return false;
        if (a.getN() != b.getN()) return false;
        return true;
    }

    private static void moveChildren(CompositeItem from, ListIterator<Item> toIterator) {
        for (ListIterator<Item> i = from.getItemIterator(); i.hasNext(); )
            toIterator.add(i.next());
    }

    /** 
     * Handle FALSE items in the immediate children of this
     * 
     * @return true if this composite was replaced by FALSE
     */
    private static boolean collapseFalse(CompositeItem composite, ListIterator<Item> parentIterator) {
        if ( ! containsFalse(composite)) return false;

        if (composite instanceof AndItem) { // AND false is always false
            parentIterator.set(new FalseItem());
            return true;
        }
        else if (composite instanceof OrItem) { // OR false is unnecessary
            removeFalseIn(composite.getItemIterator());
            return false;
        }
        else if (composite instanceof NotItem || composite instanceof RankItem) { // collapse if first, remove otherwise
            ListIterator<Item> i = composite.getItemIterator();
            if (i.next() instanceof FalseItem) {
                parentIterator.set(new FalseItem());
                return true;
            }
            else {
                removeFalseIn(i);
                return false;
            }
        }
        else { // other composites not handled
            return false;
        }
    }
    
    private static boolean containsFalse(CompositeItem composite) {
        for (ListIterator<Item> i = composite.getItemIterator(); i.hasNext(); )
            if (i.next() instanceof FalseItem) return true;
        return false;
    }

    private static void removeFalseIn(ListIterator<Item> iterator) {
        while (iterator.hasNext())
            if (iterator.next() instanceof FalseItem)
                iterator.remove();
    }
    
    private static void removeDuplicates(EquivItem composite) {
        int origSize = composite.getItemCount();
        for (int i = origSize - 1; i >= 1; --i) {
            Item deleteCandidate = composite.getItem(i);
            for (int j = 0; j < i; ++j) {
                Item check = composite.getItem(j);
                if (deleteCandidate.getClass() == check.getClass()) {
                    if (deleteCandidate instanceof PhraseItem) {
                        PhraseItem phraseDeletionCandidate = (PhraseItem) deleteCandidate;
                        PhraseItem phraseToCheck = (PhraseItem) check;
                        if (phraseDeletionCandidate.getIndexedString().equals(phraseToCheck.getIndexedString())) {
                            composite.removeItem(i);
                            break;
                        }
                    } else if (deleteCandidate instanceof PhraseSegmentItem) {
                        PhraseSegmentItem phraseSegmentDeletionCandidate = (PhraseSegmentItem) deleteCandidate;
                        PhraseSegmentItem phraseSegmentToCheck = (PhraseSegmentItem) check;
                        if (phraseSegmentDeletionCandidate.getIndexedString().equals(phraseSegmentToCheck.getIndexedString())) {
                            composite.removeItem(i);
                            break;
                        }
                    } else if (deleteCandidate instanceof BlockItem) {
                        BlockItem blockDeletionCandidate = (BlockItem) deleteCandidate;
                        BlockItem blockToCheck = (BlockItem) check;
                        if (blockDeletionCandidate.stringValue().equals(blockToCheck.stringValue())) {
                            composite.removeItem(i);
                            break;
                        }
                    }
                }
            }
        }
    }

    /**
     * If a term is present as both a rank term (i.e not the first child) and in
     * the match condition (first child), then turn off any rank calculation for
     * the term during matching, as it will be made available anyway for matches
     * by the same term in the rank part.
     *
     * @param rankItem an item which will be simplified in place
     */
    private static void makeDuplicatesCheap(RankItem rankItem) {
        // Collect terms used for ranking
        Set<TermItem> rankTerms = new HashSet<>();
        for (int i = 1; i < rankItem.getItemCount(); i++) {
            if (rankItem.getItem(i) instanceof TermItem)
                rankTerms.add((TermItem)rankItem.getItem(i));
        }

        // Make terms used for matching cheap if they also are ranking terms
        makeDuplicatesCheap(rankItem.getItem(0), rankTerms);
    }

    private static void makeDuplicatesCheap(Item item, Set<TermItem> rankTerms) {
        if (item instanceof CompositeItem) {
            for (ListIterator<Item> i = ((CompositeItem)item).getItemIterator(); i.hasNext();)
                makeDuplicatesCheap(i.next(), rankTerms);
        }
        else if (rankTerms.contains(item)) {
            item.setRanked(false);
            item.setPositionData(false);
        }
    }

    public static class CanonicalizationResult {

        private final Optional<String> error;

        private CanonicalizationResult(Optional<String> error) {
            this.error = error;
        }
        
        /** Returns the error of this query, or empty if it is a valid query */
        public Optional<String> error() {
            return error;
        }
    
        public static CanonicalizationResult error(String error) {
            return new CanonicalizationResult(Optional.of(error));
        }

        public static CanonicalizationResult success() {
            return new CanonicalizationResult(Optional.empty());
        }
        
        public boolean isError() { return error.isPresent(); }

    }

}
