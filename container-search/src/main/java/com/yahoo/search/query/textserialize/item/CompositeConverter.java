// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.textserialize.item;

import com.yahoo.prelude.query.CompositeItem;
import com.yahoo.prelude.query.Item;
import com.yahoo.search.query.textserialize.serializer.DispatchForm;
import com.yahoo.search.query.textserialize.serializer.ItemIdMapper;

import java.lang.reflect.InvocationTargetException;
import java.util.ListIterator;

/**
 * @author Tony Vaagenes
 */
public class CompositeConverter<T extends CompositeItem> implements ItemFormConverter {
    private final Class<T> itemClass;

    public CompositeConverter(Class<T> itemClass) {
        this.itemClass = itemClass;
    }

    @Override
    public Object formToItem(String name, ItemArguments arguments, ItemContext itemContext) {
        T item = newInstance();
        addChildren(item, arguments, itemContext);
        return item;
    }

    protected void addChildren(T item, ItemArguments arguments, ItemContext itemContext) {
        for (Object child : arguments.children) {
            item.addItem(asItem(child));
        }
        ItemInitializer.initialize(item, arguments, itemContext);
    }

    private static Item asItem(Object child) {
        if (!(child instanceof Item) && child != null) {
            throw new RuntimeException("Expected query item, but got '" + child.toString() +
                    "' [" + child.getClass().getName() + "]");
        }
        return (Item) child;
    }

    private T newInstance() {
        try {
            return itemClass.getDeclaredConstructor().newInstance();
        } catch (NoSuchMethodException | InvocationTargetException | InstantiationException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public DispatchForm itemToForm(Item item, ItemIdMapper itemIdMapper) {
        CompositeItem compositeItem = (CompositeItem) item;

        DispatchForm form = new DispatchForm(getFormName(item));
        for (ListIterator<Item> i = compositeItem.getItemIterator(); i.hasNext() ;) {
            form.addChild(i.next());
        }
        ItemInitializer.initializeForm(form, item, itemIdMapper);
        return form;
    }

    protected String getFormName(Item item) {
        return item.getItemType().name();
    }
}
