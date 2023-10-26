// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.textserialize.item;

import com.yahoo.prelude.query.AndItem;
import com.yahoo.prelude.query.CompositeItem;
import com.yahoo.prelude.query.EquivItem;
import com.yahoo.prelude.query.Item;
import com.yahoo.prelude.query.NearItem;
import com.yahoo.prelude.query.ONearItem;
import com.yahoo.prelude.query.OrItem;
import com.yahoo.prelude.query.PhraseItem;
import com.yahoo.prelude.query.RankItem;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Tony Vaagenes
 */
public class ItemExecutorRegistry {

    private static final Map<String, ItemFormConverter> executorsByName = new HashMap<>();
    static {
        register(Item.ItemType.AND, createCompositeConverter(AndItem.class));
        register(Item.ItemType.OR, createCompositeConverter(OrItem.class));
        register(Item.ItemType.RANK, createCompositeConverter(RankItem.class));
        register(Item.ItemType.PHRASE, createCompositeConverter(PhraseItem.class));
        register(Item.ItemType.EQUIV, createCompositeConverter(EquivItem.class));

        register(AndNotRestConverter.andNotRest, new AndNotRestConverter());

        register(Item.ItemType.NEAR, new NearConverter(NearItem.class));
        register(Item.ItemType.ONEAR, new NearConverter(ONearItem.class));

        register(Item.ItemType.WORD, new WordConverter());
        register(Item.ItemType.INT, new IntConverter());
        register(Item.ItemType.PREFIX, new PrefixConverter());
        register(Item.ItemType.SUBSTRING, new SubStringConverter());
        register(Item.ItemType.EXACT, new ExactStringConverter());
        register(Item.ItemType.SUFFIX, new SuffixConverter());
    }

    private static <T extends CompositeItem> ItemFormConverter createCompositeConverter(Class<T> itemClass) {
        return new CompositeConverter<>(itemClass);
    }

    private static void register(Item.ItemType type, ItemFormConverter executor) {
        register(type.toString(), executor);
    }

    private static void register(String type, ItemFormConverter executor) {
        executorsByName.put(type, executor);
    }

    public static ItemFormConverter getByName(String name) {
        ItemFormConverter executor = executorsByName.get(name);
        ensureNotNull(executor, name);
        return executor;
    }

    private static void ensureNotNull(ItemFormConverter executor, String name) {
        if (executor == null) {
            throw new RuntimeException("No item type named '" + name + "'.");
        }
    }

    public static ItemFormConverter getByType(Item.ItemType itemType) {
        String name = (itemType == Item.ItemType.NOT) ? AndNotRestConverter.andNotRest : itemType.name();
        return getByName(name);
    }
}
