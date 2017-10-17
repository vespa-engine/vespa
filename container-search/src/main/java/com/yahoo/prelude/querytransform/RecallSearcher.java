// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.querytransform;

import com.yahoo.component.chain.dependencies.After;
import com.yahoo.component.chain.dependencies.Before;
import com.yahoo.prelude.query.*;
import com.yahoo.prelude.query.parser.AnyParser;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.processing.request.CompoundName;
import com.yahoo.search.query.QueryTree;
import com.yahoo.search.query.parser.Parsable;
import com.yahoo.search.query.parser.ParserEnvironment;
import com.yahoo.search.result.ErrorMessage;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.search.searchchain.PhaseNames;

import java.util.Iterator;
import java.util.Stack;

import static com.yahoo.prelude.querytransform.NormalizingSearcher.ACCENT_REMOVAL;
import static com.yahoo.prelude.querytransform.StemmingSearcher.STEMMING;

/**
 * This searcher parses the content of the "recall" query property as a filter expression alongside a placeholder
 * query string. The node corresponding to the placeholder query is then swapped with the current query tree. This allows
 * us to parse "recall" using the same rules as "filter" without modifying the parser.
 *
 * If the "recall" property is unset, this searcher does nothing.
 *
 * @author Simon Thoresen
 */
@After("com.yahoo.search.querytransform.WandSearcher")
@Before({STEMMING, ACCENT_REMOVAL})
public class RecallSearcher extends Searcher {

    private static final CompoundName recallName=new CompoundName("recall");

    @Override
    public com.yahoo.search.Result search(Query query, Execution execution) {
        String recall = query.properties().getString(recallName);
        if (recall == null) return execution.search(query);

        AnyParser parser = new AnyParser(ParserEnvironment.fromExecutionContext(execution.context()));
        QueryTree root = parser.parse(Parsable.fromQueryModel(query.getModel()).setQuery("foo").setFilter(recall));
        String err;
        if (root.getRoot() instanceof NullItem) {
            err = "Failed to parse recall parameter.";
        } else if (!(root.getRoot() instanceof CompositeItem)) {
            err = "Expected CompositeItem root node, got " + root.getClass().getSimpleName() + ".";
        } else if (hasRankItem(root.getRoot())) {
            query.getModel().getQueryTree().setRoot(root.getRoot());
            err = "Recall contains at least one rank item.";
        } else {
            WordItem placeholder = findOrigWordItem(root.getRoot(), "foo");
            if (placeholder == null) {
                err = "Could not find placeholder workQuery root.";
            } else {
                updateFilterTerms(root);
                CompositeItem parent = placeholder.getParent();
                parent.setItem(parent.getItemIndex(placeholder), query.getModel().getQueryTree().getRoot());
                query.getModel().getQueryTree().setRoot(root.getRoot());

                query.trace("ANDed recall tree with root workQuery node.", true, 3);
                return execution.search(query);
            }
        }
        return new Result(query, ErrorMessage.createInvalidQueryParameter(err));
    }

    /**
     * Returns true if the given item tree contains at least one instance of {@link RankItem}.
     *
     * @param root The root of the tree to check.
     * @return True if a rank item was found.
     */
    private static boolean hasRankItem(Item root) {
        Stack<Item> stack = new Stack<>();
        stack.push(root);
        while (!stack.isEmpty()) {
            Item item = stack.pop();
            if (item instanceof RankItem) {
                return true;
            }
            if (item instanceof CompositeItem) {
                CompositeItem lst = (CompositeItem)item;
                for (Iterator<Item> it = lst.getItemIterator(); it.hasNext();) {
                    stack.push(it.next());
                }
            }
        }
        return false;
    }

    /**
     * Returns the first word item contained in the given item tree that is an instance of {@link WordItem} with the
     * given word value.
     *
     * @param root The root of the tree to check.
     * @param value The word to look for.
     * @return The first node found.
     */
    private static WordItem findOrigWordItem(Item root, String value) {
        Stack<Item> stack = new Stack<>();
        stack.push(root);
        while (!stack.isEmpty()) {
            Item item = stack.pop();
            if (item.getCreator() == Item.ItemCreator.ORIG &&
                item instanceof WordItem)
            {
                WordItem word = (WordItem)item;
                if (word.getWord().equals(value)) {
                    return word;
                }
            }
            if (item instanceof CompositeItem) {
                CompositeItem lst = (CompositeItem)item;
                for (Iterator<Item> it = lst.getItemIterator(); it.hasNext();) {
                    stack.push(it.next());
                }
            }
        }
        return null;
    }

    /**
     * Marks all filter terms in the given query tree as unranked.
     *
     * @param root The root of the tree to update.
     */
    private static void updateFilterTerms(Item root) {
        Stack<Item> stack = new Stack<>();
        stack.push(root);
        while (!stack.isEmpty()) {
            Item item = stack.pop();
            if (item.getCreator() == Item.ItemCreator.FILTER) {
                item.setRanked(false);
            }
            if (item instanceof CompositeItem) {
                CompositeItem lst = (CompositeItem)item;
                for (Iterator<Item> it = lst.getItemIterator(); it.hasNext();) {
                    stack.push(it.next());
                }
            }
        }
    }
}
