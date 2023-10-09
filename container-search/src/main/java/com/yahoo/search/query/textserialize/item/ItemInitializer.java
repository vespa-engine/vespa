// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.textserialize.item;

import com.yahoo.prelude.query.IndexedItem;
import com.yahoo.prelude.query.Item;
import com.yahoo.prelude.query.TaggableItem;
import com.yahoo.search.query.textserialize.serializer.DispatchForm;
import com.yahoo.search.query.textserialize.serializer.ItemIdMapper;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * @author Tony Vaagenes
 */
public class ItemInitializer {

    private static final String indexProperty = "index";
    private static final String idProperty = "id";
    private static final String significanceProperty = "significance";
    private static final String uniqueIdProperty = "uniqueId";
    private static final String weightProperty = "weight";

    public static void initialize(Item item, ItemArguments arguments, ItemContext itemContext) {
        storeIdInContext(item, arguments.properties, itemContext);

        Object weight = arguments.properties.get(weightProperty);
        if (weight != null) {
            TypeCheck.ensureInstanceOf(weight, Number.class);
            item.setWeight(((Number)weight).intValue());
        }

        if (item instanceof TaggableItem) {
            initializeTaggableItem((TaggableItem)item, arguments, itemContext);
        }

        if (item instanceof IndexedItem) {
            initializeIndexedItem((IndexedItem)item, arguments, itemContext);
        }
    }

    private static void storeIdInContext(Item item, Map<?, ?> properties, ItemContext itemContext) {
        Object id = properties.get("id");
        if (id != null) {
            TypeCheck.ensureInstanceOf(id, String.class);
            itemContext.setItemId((String) id, item);
        }
    }

    private static void initializeTaggableItem(TaggableItem item, ItemArguments arguments, ItemContext itemContext) {
        Object connectivity = arguments.properties.get("connectivity");
        if (connectivity != null) {
            storeConnectivityInContext(item, connectivity, itemContext);
        }

        Object significance = arguments.properties.get(significanceProperty);
        if (significance != null) {
            TypeCheck.ensureInstanceOf(significance, Number.class);
            item.setSignificance(((Number)significance).doubleValue());
        }

        Object uniqueId = arguments.properties.get(uniqueIdProperty);
        if (uniqueId != null) {
            TypeCheck.ensureInstanceOf(uniqueId, Number.class);
            item.setUniqueID(((Number)uniqueId).intValue());
        }
    }

    private static void initializeIndexedItem(IndexedItem indexedItem, ItemArguments arguments, ItemContext itemContext) {
        Object index = arguments.properties.get(indexProperty);
        if (index != null) {
            TypeCheck.ensureInstanceOf(index, String.class);
            indexedItem.setIndexName((String) index);
        }
    }

    private static void storeConnectivityInContext(TaggableItem item, Object connectivity, ItemContext itemContext) {
        TypeCheck.ensureInstanceOf(connectivity, List.class);
        List<?> connectivityList = (List<?>) connectivity;
        if (connectivityList.size() != 2) {
            throw new IllegalArgumentException("Expected two elements for connectivity, got " + connectivityList.size());
        }

        Object id = connectivityList.get(0);
        Object strength = connectivityList.get(1);

        TypeCheck.ensureInstanceOf(id, String.class);
        TypeCheck.ensureInstanceOf(strength, Number.class);

        itemContext.setConnectivity(item, (String)id, ((Number)strength).doubleValue());
    }

    public static void initializeForm(DispatchForm form, Item item, ItemIdMapper itemIdMapper) {
        if (item.getWeight() != Item.DEFAULT_WEIGHT) {
            form.setProperty(weightProperty, item.getWeight());
        }

        if (item instanceof IndexedItem) {
            initializeIndexedForm(form, (IndexedItem) item);
        }
        if (item instanceof TaggableItem) {
            initializeTaggableForm(form, (TaggableItem) item, itemIdMapper);
        }
        initializeFormWithIdIfConnected(form, item, itemIdMapper);
    }

    private static void initializeFormWithIdIfConnected(DispatchForm form, Item item, ItemIdMapper itemIdMapper) {
        if (item.hasConnectivityBackLink()) {
            form.setProperty(idProperty, itemIdMapper.getId(item));
        }
    }

    @SuppressWarnings("unchecked")
    private static void initializeTaggableForm(DispatchForm form, TaggableItem taggableItem, ItemIdMapper itemIdMapper) {
        Item connectedItem = taggableItem.getConnectedItem();
        if (connectedItem != null) {
            form.setProperty("connectivity",
                    Arrays.asList(itemIdMapper.getId(connectedItem), taggableItem.getConnectivity()));
        }

        if (taggableItem.hasExplicitSignificance()) {
            form.setProperty(significanceProperty, taggableItem.getSignificance());
        }

        if (taggableItem.hasUniqueID()) {
            form.setProperty(uniqueIdProperty, taggableItem.getUniqueID());
        }
    }

    private static void initializeIndexedForm(DispatchForm form, IndexedItem item) {
        String index = item.getIndexName();
        if (!index.isEmpty()) {
            form.setProperty(indexProperty, index);
        }
    }

}
