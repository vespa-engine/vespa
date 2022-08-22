// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query.parser;

import com.yahoo.prelude.query.AndItem;
import com.yahoo.prelude.query.CompositeItem;
import com.yahoo.prelude.query.Item;
import com.yahoo.prelude.query.NotItem;
import com.yahoo.prelude.query.OrItem;
import com.yahoo.prelude.query.WordItem;
import com.yahoo.search.query.parser.ParserEnvironment;

/**
 * Parser for web search queries. Language:
 *
 * <ul>
 * <li>+item: always include this item as-is when searching (term becomes <i>protected</i>)
 * <li>-item: Exclude item when searching (terms becomes <i>protected</i>)
 * <li>a OR b (capital or): Or search for a or b
 * <li>"a b": Phrase search for a followed by b
 * </ul>
 *
 * @author bratseth
 */
public class WebParser extends AllParser {

    public WebParser(ParserEnvironment environment) {
        super(environment, false);
    }

    @Override
    protected Item parseItemsBody() {
        // Algorithm: Collect positive, negative, and'ed and or'ed elements, then combine.
        CompositeItem and = null;
        OrItem or = null;
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

            if (current != null) {
                if (and != null && (current instanceof WordItem) && "OR".equals(((WordItem)current).getRawWord())) {
                    if (or == null)
                        or = addOr(and, or);
                    and = new AndItem();
                    or.addItem(and);
                }
                else {
                    and = addAnd(current, and);
                }
            }

            if (current == null) // Change
                tokens.skip();
        } while (tokens.hasNext());

        // Combine the items
        Item topLevel = and;

        if (or != null)
            topLevel = or;

        if (not != null && topLevel != null) {
            not.setPositiveItem(topLevel);
            topLevel = not;
        }

        return simplifyUnnecessaryComposites(topLevel);
    }

}
