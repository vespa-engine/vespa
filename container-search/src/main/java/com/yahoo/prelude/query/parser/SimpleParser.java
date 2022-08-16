// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query.parser;

import com.yahoo.prelude.query.AndItem;
import com.yahoo.prelude.query.BlockItem;
import com.yahoo.prelude.query.CompositeItem;
import com.yahoo.prelude.query.Item;
import com.yahoo.prelude.query.NotItem;
import com.yahoo.prelude.query.OrItem;
import com.yahoo.prelude.query.PhraseItem;
import com.yahoo.prelude.query.RankItem;
import com.yahoo.prelude.query.TermItem;
import com.yahoo.prelude.query.TrueItem;
import com.yahoo.search.query.parser.ParserEnvironment;

import java.util.Iterator;

import static com.yahoo.prelude.query.parser.Token.Kind.PLUS;
import static com.yahoo.prelude.query.parser.Token.Kind.SPACE;

/**
 * Base class for parsers of the "simple" query languages (query types ANY and ALL).
 *
 * @author Steinar Knutsen
 * @author bratseth
 */
abstract class SimpleParser extends StructuredParser {

    protected SimpleParser(ParserEnvironment environment) {
        super(environment);
    }

    protected Item handleComposite(boolean topLevel) {
        return anyItems(false); // Nesteds are any even if all on top level
    }

    protected abstract Item negativeItem();

    /**
     * A collection of one or more items.
     * More items are collected in the default composite - or.
     * If there's a explicit composite and some other terms,
     * a rank terms combines them
     */
    protected Item anyItems(boolean topLevel) {
        int position = tokens.getPosition();
        Item item = null;

        try {
            item = anyItemsBody(topLevel);
            return item;
        } finally {
            if (item == null) {
                tokens.setPosition(position);
            }
        }
    }

    private Item anyItemsBody(boolean topLevel) {
        Item topLevelItem = null;
        NotItem not = null;
        Item item;
        do {
            item = positiveItem();
            if (item != null) {
                if (not == null) {
                    not = new NotItem();
                    not.addPositiveItem(item);
                    topLevelItem = combineItems(topLevelItem, not);
                } else {
                    not.addPositiveItem(item);
                }
            }

            if (item == null) {
                item = negativeItem();
                if (item != null) {
                    if (not == null) {
                        not = new NotItem();
                        not.addNegativeItem(item);
                        topLevelItem = combineItems(topLevelItem, not);
                    } else {
                        not.addNegativeItem(item);
                    }
                }
            }

            if (item == null) {
                item = compositeItem();
                if (item != null) {
                    if (topLevelItem == null) {
                        topLevelItem = item;
                    } else {
                        topLevelItem = combineItems(topLevelItem, item);
                    }
                }
            }

            if (item == null) {
                item = indexableItem().getFirst();
                if (item != null) {
                    if (topLevelItem == null) {
                        topLevelItem = item;
                    } else if (needNewORTopLevel(topLevelItem, item)) {
                        CompositeItem newTop = new OrItem();
                        newTop.addItem(topLevelItem);
                        newTop.addItem(item);
                        topLevelItem = newTop;
                    } else if (topLevelItem instanceof NotItem) {
                        topLevelItem = combineItems(topLevelItem, item);
                    } else {
                        ((CompositeItem) topLevelItem).addItem(item);
                    }
                }
            }

            if (topLevel && item == null) {
                tokens.skip();
            }
        } while (tokens.hasNext() && (topLevel || item != null));

        if (not != null && not.getItemCount() == 1) {
            // Incomplete not, only positive
            // => pass the positive upwards instead, drop the not
            if (topLevelItem == null || topLevelItem == not) {
                return not.removeItem(0); // The positive
            } else if (topLevelItem instanceof RankItem) {
                removeNot((RankItem) topLevelItem);
                return combineItems(topLevelItem, not.getPositiveItem());
            }
        }
        if (not != null && not.getPositiveItem() instanceof TrueItem) {
            // Incomplete not, only negatives -

            if (topLevelItem != null && topLevelItem != not) {
                // => neutral rank items becomes implicit positives
                not.addPositiveItem(getItemAsPositiveItem(topLevelItem, not));
                return not;
            } else { // Only negatives - ignore them
                return null;
            }
        }
        if (topLevelItem != null) {
            return topLevelItem;
        } else {
            return not;
        }
    }

    /** Says whether we need a new top level OR item given the new item */
    private boolean needNewORTopLevel(Item topLevelItem, Item item) {
        if (item == null) return false;
        if (topLevelItem instanceof TermItem) return true;
        if (topLevelItem instanceof PhraseItem) return true;
        if (topLevelItem instanceof BlockItem) return true;
        if ( topLevelItem instanceof AndItem) return true;
        return false;
    }

    /** Removes and returns the first <i>not</i> found in the composite, or returns null if there's none */
    private NotItem removeNot(CompositeItem composite) {
        for (int i = 0; i < composite.getItemCount(); i++) {
            if (composite.getItem(i) instanceof NotItem) {
                return (NotItem) composite.removeItem(i);
            }
        }
        return null;
    }

    protected abstract Item combineItems(Item topLevelItem, Item item);

    protected Item positiveItem() {
        int position = tokens.getPosition();
        Item item = null;

        try {
            if ( ! tokens.skipMultiple(PLUS)) {
                return null;
            }

            if (tokens.currentIsNoIgnore(SPACE)) {
                return null;
            }

            item = indexableItem().getFirst();

            if (item == null) {
                item = compositeItem();
            }
            if (item!=null)
                item.setProtected(true);
            return item;
        } finally {
            if (item == null) {
                tokens.setPosition(position);
            }
        }
    }

    /**
     * Returns the content of the given item as an item to be added as a positive item.
     * Used to turn a top level item into implicit positives when explicit positives
     * (+ items) are not found, but negatives are.
     */
    private Item getItemAsPositiveItem(Item item, NotItem not) {
        if (!(item instanceof RankItem rank)) {
            return item;
        }

        // Remove the not from the rank item, the rank should generally
        // be the first, but this is not always the case
        int limit = rank.getItemCount();
        int n = 0;

        while (n < limit) {
            if (rank.getItem(n) == not) {
                rank.removeItem(n);
                break;
            }
            n++;
        }

        if (rank.getItemCount() == 1) {
            return rank.getItem(0);
        }

        // Several items - or together
        OrItem or = new OrItem();

        for (Iterator<Item> i = rank.getItemIterator(); i.hasNext();) {
            or.addItem(i.next());
        }
        return or;
    }

}
