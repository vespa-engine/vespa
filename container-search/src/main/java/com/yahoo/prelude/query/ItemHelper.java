// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query;

import java.util.Iterator;
import java.util.List;

/**
 * Helper functions for Item
 *
 * @author Arne Bergene Fossaa
 */
public class ItemHelper {

        /*
        We could have exchanged the following 3 functions with this
        But this introspection is a bit too much of a hack, so we'll leave it with this.


        public static <T extends CompositeItem> T ensureIsItem(Item unknown,Class<T> tClass) {

            if(unknown != null &&  tClass.isInstance(unknown)) {
                return (T) unknown;
            }
            T item;

            try {
                Constructor<T> n = tClass.getConstructor();
                item = n.newInstance();
            } catch (NoSuchMethodException e) {
                return null;
            } catch (InvocationTargetException e) {
                return null;
            } catch (IllegalAccessException e) {
                return null;
            } catch (InstantiationException e) {
                return null;
            }
            if(item != null) {
                item.addItem(unknown);
            }
            return item;

        }
        */

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
