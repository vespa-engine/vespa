// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.textserialize.item;

import com.yahoo.prelude.query.Item;
import com.yahoo.prelude.query.NearItem;
import com.yahoo.search.query.textserialize.serializer.DispatchForm;
import com.yahoo.search.query.textserialize.serializer.ItemIdMapper;

/**
 * @author Tony Vaagenes
 */
@SuppressWarnings("rawtypes")
public class NearConverter extends CompositeConverter {
    final private String distanceProperty = "distance";;

    @SuppressWarnings("unchecked")
    public NearConverter(Class<? extends NearItem> nearItemClass) {
        super(nearItemClass);
    }

    @Override
    public Object formToItem(String name, ItemArguments arguments, ItemContext itemContext) {
        NearItem nearItem = (NearItem) super.formToItem(name, arguments, itemContext);
        setDistance(nearItem, arguments);
        return nearItem;
    }

    private void setDistance(NearItem nearItem, ItemArguments arguments) {
        Object distance = arguments.properties.get(distanceProperty);
        if (distance != null) {
            TypeCheck.ensureInteger(distance);
            nearItem.setDistance(((Number)distance).intValue());
        }
    }

    @Override
    public DispatchForm itemToForm(Item item, ItemIdMapper itemIdMapper) {
        DispatchForm dispatchForm = super.itemToForm(item, itemIdMapper);

        NearItem nearItem = (NearItem)item;
        dispatchForm.setProperty(distanceProperty, nearItem.getDistance());
        return dispatchForm;
    }
}
