// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.textserialize.item;

import com.yahoo.prelude.query.Item;
import com.yahoo.prelude.query.NotItem;

import java.util.List;

import static com.yahoo.search.query.textserialize.item.ListUtil.butFirst;
import static com.yahoo.search.query.textserialize.item.ListUtil.first;

/**
 * @author Tony Vaagenes
 */
public class AndNotRestConverter extends CompositeConverter<NotItem> {

    static final String andNotRest = "AND-NOT-REST";

    public AndNotRestConverter() {
        super(NotItem.class);
    }

    @Override
    protected void addChildren(NotItem item, ItemArguments arguments, ItemContext context) {
        if (firstIsNull(arguments.children)) {
            addNegativeItems(item, arguments.children);
        } else {
            addItems(item, arguments.children);
        }
    }

    private void addNegativeItems(NotItem notItem, List<Object> children) {
        for (Object child: butFirst(children)) {
            TypeCheck.ensureInstanceOf(child, Item.class);
            notItem.addNegativeItem((Item) child);
        }
    }

    private void addItems(NotItem notItem, List<Object> children) {
        for (Object child : children) {
            TypeCheck.ensureInstanceOf(child, Item.class);
            notItem.addItem((Item) child);
        }
    }


    private boolean firstIsNull(List<Object> children) {
        return !children.isEmpty() && first(children) == null;
    }

    @Override
    protected String getFormName(Item item) {
        return andNotRest;
    }

}
