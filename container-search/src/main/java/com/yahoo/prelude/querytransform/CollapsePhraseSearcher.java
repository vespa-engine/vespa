// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.querytransform;

import java.util.ListIterator;

import com.yahoo.prelude.query.CompositeItem;
import com.yahoo.prelude.query.Item;
import com.yahoo.prelude.query.PhraseItem;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.query.QueryTree;
import com.yahoo.search.searchchain.Execution;

/**
 * Make single item phrases in query into single word items.
 *
 * @author  <a href="mailto:steinar@yahoo-inc.com">Steinar Knutsen</a>
 */
public class CollapsePhraseSearcher extends Searcher {
    public Result search(Query query, Execution execution) {
        QueryTree tree = query.getModel().getQueryTree();
        Item root = tree.getRoot();
        if (root != null) {
            Item newRoot = root.clone();
            newRoot = simplifyPhrases(newRoot);
            // Sets new root instead of transforming the query tree
            // to make code nicer if the root is a single term phrase
            if (!root.equals(newRoot)) {
                tree.setRoot(newRoot);
                query.trace("Collapsing single term phrases to single terms",
                    true, 2);
            }
        }
        return execution.search(query);
    }


    private Item simplifyPhrases(Item root) {
        if (root == null) {
            return root;
        }
        else if (root instanceof PhraseItem) {
            return collapsePhrase((PhraseItem)root);
        }
        else if (root instanceof CompositeItem) {
            CompositeItem composite = (CompositeItem)root;
            ListIterator<Item> i = composite.getItemIterator();
            while (i.hasNext()) {
                Item original = i.next();
                Item transformed = simplifyPhrases(original);
                if (original != transformed)
                    i.set(transformed);
            }
            return root;
        }
        else {
            return root;
        }
    }
    private Item collapsePhrase(PhraseItem root) {
        if (root.getItemCount() == 1)
            return root.getItem(0);
        else
            return root;
    }
}
