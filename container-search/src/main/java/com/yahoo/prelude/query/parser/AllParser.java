// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query.parser;

import com.yahoo.prelude.query.AndItem;
import com.yahoo.prelude.query.CompositeItem;
import com.yahoo.prelude.query.IntItem;
import com.yahoo.prelude.query.Item;
import com.yahoo.prelude.query.NotItem;
import com.yahoo.prelude.query.NullItem;
import com.yahoo.prelude.query.OrItem;
import com.yahoo.prelude.query.PhraseItem;
import com.yahoo.prelude.query.QueryCanonicalizer;
import com.yahoo.prelude.query.RankItem;
import com.yahoo.prelude.query.WeakAndItem;
import com.yahoo.search.query.QueryTree;
import com.yahoo.search.query.parser.ParserEnvironment;

import java.util.Iterator;

import static com.yahoo.prelude.query.parser.Token.Kind.MINUS;
import static com.yahoo.prelude.query.parser.Token.Kind.SPACE;

/**
 * Parser for queries of type all and weakAnd.
 *
 * @author Steinar Knutsen
 * @author bratseth
 */
public class AllParser extends SimpleParser {

    private final boolean weakAnd;

    /**
     * Creates an all/weakAnd parser
     *
     * @param weakAnd false to parse into AndItem (by default), true to parse to WeakAndItem
     */
    public AllParser(ParserEnvironment environment, boolean weakAnd) {
        super(environment);
        this.weakAnd = weakAnd;
    }

    @Override
    protected Item parseItems() {
        int position = tokens.getPosition();
        try {
            return parseItemsBody();
        } finally {
            tokens.setPosition(position);
        }
    }

    protected Item parseItemsBody() {
        // Algorithm: Collect positive, negative, and and'ed items, then combine.
        CompositeItem and = null;
        NotItem not = null; // Store negatives here as we go
        Item current;
        // Find all items
        do {
            current = negativeItem();
            if (current != null) {
                not = addNot(current, not);
                continue;
            }

            current = positiveItem();
            if (current == null)
                current = indexableItem().getFirst();
            if (current == null)
                current = compositeItem();

            if (current != null)
                and = addAnd(current, and);

            if (current == null)
                tokens.skip();
        } while (tokens.hasNext());

        // Combine the items
        Item topLevel = and;

        if (not != null && topLevel != null) {
            not.setPositiveItem(topLevel);
            topLevel = not;
        }

        return simplifyUnnecessaryComposites(topLevel);
    }

    // Simplify if there are unnecessary composites due to single elements
    protected final Item simplifyUnnecessaryComposites(Item item) {
        if (item == null) return null;

        QueryTree root = new QueryTree(item);
        QueryCanonicalizer.canonicalize(root);

        return root.getRoot() instanceof NullItem ? null : root.getRoot();
    }

    protected CompositeItem addAnd(Item item, CompositeItem and) {
        if (and == null)
            and = createAnd();
        and.addItem(item);
        return and;
    }

    private CompositeItem createAnd() {
        return weakAnd ? new WeakAndItem() : new AndItem();
    }

    protected OrItem addOr(Item item, OrItem or) {
        if (or == null)
            or = new OrItem();
        or.addItem(item);
        return or;
    }

    protected NotItem addNot(Item item, NotItem not) {
        if (not == null)
            not = new NotItem();
        not.addNegativeItem(item);
        return not;
    }

    protected Item negativeItem() {
        int position = tokens.getPosition();
        Item item = null;
        boolean isComposited = false;
        try {
            if ( ! tokens.skip(MINUS)) return null;
            if (tokens.currentIsNoIgnore(SPACE)) return null;
            var itemAndExplicitIndex = indexableItem();
            item = itemAndExplicitIndex.getFirst();
            boolean explicitIndex = itemAndExplicitIndex.getSecond();
            if (item == null) {
                item = compositeItem();

                if (item != null) {
                    isComposited = true;
                    if (item instanceof OrItem) { // Turn into And
                        CompositeItem and = createAnd();

                        for (Iterator<Item> i = ((OrItem) item).getItemIterator(); i.hasNext();) {
                            and.addItem(i.next());
                        }
                        item = and;
                    }
                }
            }
            if (item != null)
                item.setProtected(true);

            // Heuristic overdrive engaged!
            // Interpret -N as a positive item matching a negative number (by backtracking out of this)
            // but not if there is an explicit index (such as -a:b)
            // but interpret -(N) as a negative item matching a positive number
            // but interpret --N as a negative item matching a negative number
            if (item instanceof IntItem &&
                ! explicitIndex &&
                ! isComposited &&
                ! ((IntItem)item).getNumber().startsWith(("-"))) {
                item = null;
            }
            return item;
        } finally {
            if (item == null) {
                tokens.setPosition(position);
            }
        }
    }

    /**
     * Returns the top level item resulting from combining the given top
     * level item and the new item. This implements most of the weird transformation
     * rules of the parser.
     */
    protected Item combineItems(Item topLevelItem, Item item) {
        if (topLevelItem == null) {
            return item;
        } else if (topLevelItem instanceof OrItem && item instanceof OrItem) {
            OrItem newTopOr = new OrItem();

            newTopOr.addItem(topLevelItem);
            newTopOr.addItem(item);
            return newTopOr;
        } else if (item instanceof OrItem && topLevelItem instanceof RankItem) {
            for (Iterator<Item> i = ((RankItem) topLevelItem).getItemIterator(); i.hasNext();) {
                ((OrItem) item).addItem(0, i.next());
            }
            return item;
        } else if (item instanceof OrItem && topLevelItem instanceof PhraseItem) {
            OrItem newTopOr = new OrItem();

            newTopOr.addItem(topLevelItem);
            newTopOr.addItem(item);
            return newTopOr;
        } else if (!(topLevelItem instanceof RankItem)) {
            RankItem rank = new RankItem();

            if (topLevelItem instanceof NotItem) { // Strange rule, but that's how it is
                rank.addItem(topLevelItem);
                rank.addItem(item);
            } else {
                rank.addItem(item);
                rank.addItem(topLevelItem);
            }
            return rank;
        } else if ((item instanceof RankItem itemAsRank) && (((RankItem)item).getItem(0) instanceof OrItem)) {
            OrItem or = (OrItem) itemAsRank.getItem(0);

            ((RankItem) topLevelItem).addItem(0, or);
            for (int i = 1; i < itemAsRank.getItemCount(); i++) {
                or.addItem(0, itemAsRank.getItem(i));
            }
            return topLevelItem;
        } else {
            ((RankItem) topLevelItem).addItem(0, item);
            return topLevelItem;
        }
    }

}
