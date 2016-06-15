// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query;


import com.yahoo.search.Query;
import com.yahoo.search.query.QueryTree;

import java.util.*;


/**
 * A class which canonicalizes and validates queries.
 * This class is multithread safe.
 *
 * @author <a href="mailto:bratseth@yahoo-inc.com">Jon Bratseth</a>
 */
public class QueryCanonicalizer {

    /** The name of the operation performed by this (for use in search chain ordering) */
    public static final String queryCanonicalization = "queryCanonicalization";

    /**
     * Validates this query and carries out possible operations on this query
     * which simplifies it without changing its semantics.
     *
     * @return null if the query is valid, an error message if it is invalid
     */
    public static String canonicalize(Query query) {
        Item root = query.getModel().getQueryTree().getRoot();
        return canonicalize(query, root);
    }

    /**
     * Validates this query and carries out possible operations on this query
     * which simplifies it without changing its semantics.
     *
     * @return null if the query is valid, an error message if it is invalid
     */
    public static String canonicalize(QueryTree query) {
        QueryWrapper q = new QueryWrapper();
        q.setRoot(query.getRoot()); // Could get rid of the wrapper...
        treeCanonicalize(q, query.getRoot(), null);
        query.setRoot(q.root);
        return q.error;
    }

    /**
     * Validates this query and
     * carries out possible operations on this query which simplifies it
     * without changing its semantics.
     *
     * @param  item the item to canonicalize
     * @return null if the query is valid, an error message if it is invalid
     */
    private static String canonicalize(Query query, Item item) {
        QueryWrapper q = new QueryWrapper();
        q.setRoot(item);
        treeCanonicalize(q, query.getModel().getQueryTree().getRoot(), null);
        if (q.root == null)
            q.root = new NullItem();
        query.getModel().getQueryTree().setRoot(q.root);
        return q.error;
    }

    /**
     * @param bag wrapper for error message and query root
     * @param item the item to canonicalize
     * @param iterator iterator for the above item if pertinent
     * @return whether the query could be canonicalized into something
     */
    public static boolean treeCanonicalize(QueryWrapper bag, Item item, ListIterator<Item> iterator) {
        if (iterator == null && (item == null || item instanceof NullItem)) {
            bag.setError("No query");
            return false;
        }

        if (item instanceof TermItem) {
            return true;
        }

        if (item instanceof NullItem) {
            iterator.remove();
        }

        if ( ! (item instanceof CompositeItem)) {
            return true;
        } // Impossible yet
        CompositeItem composite = (CompositeItem) item;

        for (ListIterator<Item> i = composite.getItemIterator(); i.hasNext();) {
            Item child = i.next();
            boolean subtreeOK = treeCanonicalize(bag, child, i);

            if (!subtreeOK) {
                return false;
            }
        }

        if (composite instanceof EquivItem) {
            removeDuplicates((EquivItem) composite);
        }
        else if (composite instanceof RankItem) {
            makeDuplicatesCheap((RankItem)composite);
        }
        else if (composite instanceof NotItem) {
            if (((NotItem) composite).getPositiveItem() == null) {
                bag.setError("Can not search for only negative items");
                return false;
            }
        }

        if (composite.getItemCount() == 0) {
            if (iterator == null) {
                bag.setRoot(new NullItem());
                bag.setError("No query: Contained an empty " + composite.getName() + " only");
                return false;
            } else {
                iterator.remove();
            }
        }

        if (composite.getItemCount() == 1 && ! (composite instanceof NonReducibleCompositeItem)) {
            if (composite instanceof PhraseItem || composite instanceof PhraseSegmentItem) {
                composite.getItem(0).setWeight(composite.getWeight());
            }
            if (iterator == null) {
                bag.setRoot(composite.getItem(0));
            } else {
                iterator.set(composite.getItem(0));
            }
        }

        return true;
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
     * @param rankItem
     *            an item which will be simplified in place
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

    public static class QueryWrapper {
        private Item root = null;
        private String error = null;

        public Item getRoot() { return root; }
        public void setRoot(Item root) {
            this.root = root;
        }
        public String getError() {
            return error;
        }
        public void setError(String error) {
            this.error = error;
        }

    }

}
