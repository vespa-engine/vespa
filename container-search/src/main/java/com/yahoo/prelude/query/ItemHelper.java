// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query;

import java.util.Iterator;
import java.util.List;

/**
 * Helper functions for Item
 *
 * @author Arne Bergene Fossaa
 */
public class ItemHelper {

    /** Traverse the query tree and return total number of terms */
    int getNumTerms(Item rootNode) {
        int numTerms = 0;

        if (rootNode == null) {
            return 0;
        } else if (rootNode instanceof CompositeItem) {
            CompositeItem composite = (CompositeItem) rootNode;

            for (Iterator<Item> i = composite.getItemIterator(); i.hasNext();) {
                numTerms += getNumTerms(i.next());
            }
        } else if (rootNode instanceof TermItem) {
            return 1;
        } else {
            return 0;
        }
        return numTerms;
    }

    public void getPositiveTerms(Item item, List<IndexedItem> terms) {
        if (item instanceof NotItem) {
            getPositiveTerms(((NotItem) item).getPositiveItem(), terms);
        } else if (item instanceof PhraseItem) {
            PhraseItem pItem = (PhraseItem)item;
            terms.add(pItem);
        } else if (item instanceof CompositeItem) {
            for (Iterator<Item> i = ((CompositeItem) item).getItemIterator(); i.hasNext();) {
                getPositiveTerms(i.next(), terms);
            }
        } else if (item instanceof TermItem) {
            terms.add((TermItem)item);
        }
    }

}
