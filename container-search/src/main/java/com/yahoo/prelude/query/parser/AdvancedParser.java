// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query.parser;

import com.yahoo.prelude.query.*;
import com.yahoo.search.query.parser.ParserEnvironment;

import static com.yahoo.prelude.query.parser.Token.Kind.LBRACE;
import static com.yahoo.prelude.query.parser.Token.Kind.NUMBER;

/**
 * Parser for queries of type advanced.
 *
 * @author Steinar Knutsen
 * @deprecated YQL should be used for formal queries
 */
@Deprecated // DO NOT REMOVE (we'll keep this around longer)
public class AdvancedParser extends StructuredParser {

    public AdvancedParser(ParserEnvironment environment) {
        super(environment);
    }

    protected Item parseItems() {
        return advancedItems(true);
    }

    protected Item handleComposite(boolean topLevel) {
        return advancedItems(false);
    }

    /**
     * A collection of one or more advanced items.
     */
    private Item advancedItems(boolean topLevel) {
        int position = tokens.getPosition();
        Item item = null;

        try {
            item = advancedItemsBody(topLevel);
            return item;
        } finally {
            if (item == null) {
                tokens.setPosition(position);
            }
        }
    }

    private Item advancedItemsBody(boolean topLevel) {
        Item topLevelItem = null;
        Item item;
        boolean itemIsComposite;
        boolean topLevelIsClosed = false;
        boolean expectingOperator = false;

        do {
            item = null;

            if (item == null) {
                item = indexableItem();
                if (item == null) {
                    item = compositeItem();
                    itemIsComposite = true;
                } else {
                    itemIsComposite = false;
                }
                if (item != null) {
                    Item newTop = null;

                    if (expectingOperator) {
                        newTop = handleAdvancedOperator(topLevelItem, item,
                                topLevelIsClosed);
                    }
                    if (newTop != null) { // Operator found
                        topLevelIsClosed = false;
                        expectingOperator = false;
                        topLevelItem = newTop;
                    } else if (topLevelItem == null) {
                        topLevelItem = item;
                        if (itemIsComposite) {
                            topLevelIsClosed = true;
                        }
                        expectingOperator = true;
                    } else if (topLevelItem instanceof CompositeItem
                               && !(topLevelItem instanceof SegmentItem)) {
                        ((CompositeItem) topLevelItem).addItem(item);
                        expectingOperator = true;
                    } else {
                        AndItem and = new AndItem();

                        and.addItem(topLevelItem);
                        and.addItem(item);
                        topLevelItem = and;
                        topLevelIsClosed = false;
                        expectingOperator = true;
                    }
                }
            }

            if (topLevel && item == null) {
                tokens.skip();
            }
        } while (tokens.hasNext() && (topLevel || item != null));

        // Optimize away composites containing one item only
        // (including nots with only a positive)
        if (topLevelItem instanceof CompositeItem
                && ((CompositeItem) topLevelItem).getItemCount() == 1) {
            return ((CompositeItem) topLevelItem).removeItem(0);
        }

        return topLevelItem;
    }

    /** Returns whether the item is a specific word item */
    private boolean isTheWord(String word, Item item) {
        if (!(item instanceof WordItem)) {
            return false;
        }
        return word.equalsIgnoreCase(((WordItem) item).getRawWord()); // TODO: Why not search for getWord w.o lowercasing?
    }



    /** Returns the new top level, or null if the current item is not an operator */
    private Item handleAdvancedOperator(Item topLevelItem, Item item, boolean topLevelIsClosed) {
        if (isTheWord("and", item)) {
            if (topLevelIsClosed || !(topLevelItem instanceof AndItem)) {
                AndItem and = new AndItem();

                and.addItem(topLevelItem);
                return and;
            }
            return topLevelItem;
        } else if (isTheWord("or", item)) {
            if (topLevelIsClosed || !(topLevelItem instanceof OrItem)) {
                OrItem or = new OrItem();

                or.addItem(topLevelItem);
                return or;
            }
            return topLevelItem;
        } else if (isTheWord("equiv", item)) {
            if (topLevelIsClosed || !(topLevelItem instanceof EquivItem)) {
                EquivItem equiv = new EquivItem();

                equiv.addItem(topLevelItem);
                return equiv;
            }
            return topLevelItem;
        } else if (isTheWord("wand", item)) {
            int n = consumeNumericArgument();
            if (n == 0)
                n=WeakAndItem.defaultN;
            if (topLevelIsClosed || !(topLevelItem instanceof WeakAndItem) || n != ((WeakAndItem)topLevelItem).getN()) {
                WeakAndItem wand = new WeakAndItem();
                wand.setN(n);
                wand.addItem(topLevelItem);
                return wand;
            }
            return topLevelItem;
        } else if (isTheWord("andnot", item)) {
            if (topLevelIsClosed || !(topLevelItem instanceof NotItem)) {
                NotItem not = new NotItem();

                not.addPositiveItem(topLevelItem);
                return not;
            }
            return topLevelItem;
        } else if (isTheWord("rank", item)) {
            if (topLevelIsClosed || !(topLevelItem instanceof RankItem)) {
                RankItem rank = new RankItem();

                rank.addItem(topLevelItem);
                return rank;
            }
            return topLevelItem;
        } else if (isTheWord("near", item)) {
            int distance = consumeNumericArgument();
            if (distance==0)
                distance=NearItem.defaultDistance;
            if (topLevelIsClosed || !(topLevelItem instanceof NearItem) || distance!=((NearItem)topLevelItem).getDistance()) {
                NearItem near = new NearItem(distance);

                near.addItem(topLevelItem);
                return near;
            }
            return topLevelItem;
        } else if (isTheWord("onear", item)) {
            int distance = consumeNumericArgument();
            if (distance==0)
                distance=ONearItem.defaultDistance;
            if (topLevelIsClosed || !(topLevelItem instanceof ONearItem) || distance!=((ONearItem)topLevelItem).getDistance()) {
                ONearItem oNear = new ONearItem(distance);

                oNear.addItem(topLevelItem);
                return oNear;
            }
            return topLevelItem;
        }

        return null;
    }

    /** Returns the argument to this operator or 0 if none */
    private int consumeNumericArgument() {
        if (!tokens.currentIs(LBRACE)) return 0;
        tokens.skip(LBRACE);
        if (!tokens.currentIsNoIgnore(NUMBER)) throw new IllegalArgumentException("Expected an integer argument");
        int distance = Integer.valueOf(tokens.next().image);
        if (!tokens.skip(Token.Kind.RBRACE)) throw new IllegalArgumentException("Expected a right brace following the argument");
        return distance;
    }

}
