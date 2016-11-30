// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query;

import com.yahoo.search.Query;
import com.yahoo.search.query.QueryTree;

import java.util.*;

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
        return canonicalize(query.getModel().getQueryTree());
    }

    /**
     * Canonicalize this query
     * 
     * @return null if the query is valid, an error message if it is invalid
     */
    public static String canonicalize(QueryTree query) {
        ListIterator<Item> rootItemIterator = query.getItemIterator();
        CanonicalizationResult result = recursivelyCanonicalize(rootItemIterator.next(), rootItemIterator);
        if (query.isEmpty() && ! result.isError()) result = CanonicalizationResult.error("No query");
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

        collapseLevels(composite);
        
        if (composite.getItemCount() == 0) {
            parentIterator.remove();
            return CanonicalizationResult.success();
        }
            
        if (composite instanceof EquivItem) {
            removeDuplicates((EquivItem) composite);
        }
        else if (composite instanceof RankItem) {
            makeDuplicatesCheap((RankItem)composite);
        }
        else if (composite instanceof NotItem) {
            if (((NotItem) composite).getPositiveItem() == null)
                return CanonicalizationResult.error("Can not search for only negative items");
        }

        if (composite.getItemCount() == 0)
            parentIterator.remove();

        if (composite.getItemCount() == 1 && ! (composite instanceof NonReducibleCompositeItem)) {
            if (composite instanceof PhraseItem || composite instanceof PhraseSegmentItem)
                composite.getItem(0).setWeight(composite.getWeight());
            parentIterator.set(composite.getItem(0));
        }

        return CanonicalizationResult.success();
    }
    
    private static void collapseLevels(CompositeItem composite) {
        if ( ! (composite instanceof AndItem || composite instanceof OrItem)) return;

        for (ListIterator<Item> i = composite.getItemIterator(); i.hasNext(); ) {
            Item child = i.next();
            if (child.getClass() != composite.getClass()) continue;
            i.remove();
            moveChildren((CompositeItem)child, i);
        }
    }
    
    private static void moveChildren(CompositeItem from, ListIterator<Item> toIterator) {
        for (ListIterator<Item> i = from.getItemIterator(); i.hasNext(); )
            toIterator.add(i.next());
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
