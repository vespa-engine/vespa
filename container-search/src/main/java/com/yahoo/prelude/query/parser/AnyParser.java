// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query.parser;

import com.yahoo.language.Language;
import com.yahoo.prelude.IndexFacts;
import com.yahoo.prelude.query.AndItem;
import com.yahoo.prelude.query.BlockItem;
import com.yahoo.prelude.query.CompositeItem;
import com.yahoo.prelude.query.Item;
import com.yahoo.prelude.query.NotItem;
import com.yahoo.prelude.query.OrItem;
import com.yahoo.prelude.query.PhraseItem;
import com.yahoo.prelude.query.RankItem;
import com.yahoo.prelude.query.TermItem;
import com.yahoo.search.query.parser.ParserEnvironment;

import java.util.Iterator;

import static com.yahoo.prelude.query.parser.Token.Kind.*;

/**
 * Parser for queries of type any.
 *
 * @author Steinar Knutsen
 */
public class AnyParser extends SimpleParser {

    public AnyParser(ParserEnvironment environment) {
        super(environment);
    }

    @Override
    protected Item parseItems() {
        return anyItems(true);
    }

    Item parseFilter(String filter, Language queryLanguage, IndexFacts.Session indexFacts) {
        setState(queryLanguage, indexFacts, null);
        tokenize(filter, null, indexFacts, queryLanguage);

        Item filterRoot = anyItems(true);
        if (filterRoot == null) return null;

        markAllTermsAsFilters(filterRoot);
        return filterRoot;
    }

    protected Item negativeItem() {
        int position = tokens.getPosition();
        Item item = null;

        try {
            tokens.skipMultiple(PLUS);
            if ( ! tokens.skipMultiple(MINUS)) return null;
            if (tokens.currentIsNoIgnore(SPACE)) return null;

            item = indexableItem().getFirst();

            if (item == null) {
                item = compositeItem();

                if (item != null) {
                    if (item instanceof OrItem) { // Turn into And
                        AndItem and = new AndItem();

                        for (Iterator<Item> i = ((OrItem) item).getItemIterator(); i.hasNext();) {
                            and.addItem(i.next());
                        }
                        item = and;
                    }
                }
            }
            if (item != null)
                item.setProtected(true);

            return item;
        } finally {
            if (item == null)
                tokens.setPosition(position);
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
        } else if ((topLevelItem instanceof RankItem)
                && (item instanceof RankItem)
                && (((RankItem) item).getItem(0) instanceof OrItem)) {
            RankItem itemAsRank = (RankItem) item;
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

    Item applyFilter(Item root, String filter, Language queryLanguage, IndexFacts.Session indexFacts) {
        setState(queryLanguage, indexFacts, null);
        tokenize(filter, null, indexFacts, queryLanguage);
        return filterItems(root);
    }

    private void markAllTermsAsFilters(Item root) {
        if (root instanceof BlockItem) {
            root.setFilter(true);
        }

        if (root instanceof TermItem) {
            root.setFilter(true);
        } else {
            if (root instanceof PhraseItem) {
                root.setFilter(true);
            }
            for (Iterator<Item> i = ((CompositeItem) root).getItemIterator(); i.hasNext();) {
                markAllTermsAsFilters(i.next());
            }
        }
    }

    private Item filterItems(Item root) {
        while (tokens.hasNext()) {
            Item item = positiveItem();
            root = addAndFilter(root, item);
            if (item == null) {
                item = negativeItem();
                root = addNotFilter(root, item);
            }
            if (item == null) {
                item = indexableItem().getFirst();
                root = addRankFilter(root, item);
            }

            if (item != null) {
                markAllTermsAsFilters(item);
            } else {
                tokens.skip();
            }
        }
        return root;
    }

    private Item addAndFilter(Item root, Item item) {
        if (item == null) {
            return root;
        }

        if (root instanceof AndItem) {
            ((AndItem) root).addItem(item);
            return root;
        }

        if (root instanceof RankItem) {
            Item firstChild = ((RankItem) root).getItem(0);

            if (firstChild instanceof AndItem) {
                ((AndItem) firstChild).addItem(item);
                return root;
            } else if (firstChild instanceof NotItem) {
                ((NotItem) firstChild).addPositiveItem(item);
                return root;
            }
        }

        AndItem and = new AndItem();

        and.addItem(root);
        and.addItem(item);
        return and;
    }

    private Item addNotFilter(Item root, Item item) {
        if (item == null) {
            return root;
        }

        if (root instanceof NotItem) {
            ((NotItem) root).addNegativeItem(item);
            return root;
        }

        if (root instanceof RankItem) {
            RankItem rootAsRank = (RankItem) root;
            Item firstChild = rootAsRank.getItem(0);

            if (firstChild instanceof NotItem) {
                ((NotItem) firstChild).addNegativeItem(item);
                return root;
            } else {
                NotItem not = new NotItem();

                not.addPositiveItem(rootAsRank.removeItem(0));
                not.addNegativeItem(item);
                if (rootAsRank.getItemCount() == 0) {
                    return not;
                } else {
                    rootAsRank.addItem(0, not);
                    return root;
                }
            }
        }

        NotItem not = new NotItem();

        not.addPositiveItem(root);
        not.addNegativeItem(item);
        return not;
    }

    private Item addRankFilter(Item root, Item item) {
        if (item == null) {
            return root;
        }

        if (root instanceof RankItem) {
            ((RankItem) root).addItem(item);
            return root;
        }

        RankItem rank = new RankItem();

        rank.addItem(root);
        rank.addItem(item);
        return rank;
    }

}
