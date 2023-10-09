// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.textserialize.item;

import com.yahoo.prelude.query.Item;
import com.yahoo.prelude.query.TermItem;
import com.yahoo.search.query.textserialize.serializer.DispatchForm;
import com.yahoo.search.query.textserialize.serializer.ItemIdMapper;

/**
 * @author Tony Vaagenes
 */
public abstract class TermConverter implements ItemFormConverter {

    @Override
    public Object formToItem(String name, ItemArguments arguments, ItemContext context) {
        ensureOnlyOneChild(arguments);
        String word = getWord(arguments);

        TermItem item = newTermItem(word);
        ItemInitializer.initialize(item, arguments, context);
        return item;
    }

    abstract TermItem newTermItem(String word);

    private void ensureOnlyOneChild(ItemArguments arguments) {
        if (arguments.children.size() != 1) {
            throw new IllegalArgumentException("Expected exactly one argument, got '" +
                    arguments.children.toString() + "'");
        }
    }

    private String getWord(ItemArguments arguments) {
        Object word = arguments.children.get(0);

        if (!(word instanceof String)) {
            throw new RuntimeException("Expected string, got '" + word + "' [" + word.getClass().getName() + "].");
        }
        return (String)word;
    }

    @Override
    public DispatchForm itemToForm(Item item, ItemIdMapper itemIdMapper) {
        TermItem termItem = (TermItem)item;

        DispatchForm form = new DispatchForm(termItem.getItemType().name());
        ItemInitializer.initializeForm(form, item, itemIdMapper);
        form.addChild(getValue(termItem));
        return form;
    }

    protected abstract String getValue(TermItem item);

}
