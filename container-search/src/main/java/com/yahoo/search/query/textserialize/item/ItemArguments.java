// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.textserialize.item;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.yahoo.search.query.textserialize.item.ListUtil.firstInstanceOf;

/**
 * @author Tony Vaagenes
 */
public class ItemArguments {
    public final Map<?, ?> properties;
    public final List<Object> children;

    public ItemArguments(List<Object> arguments) {
        if (firstInstanceOf(arguments, Map.class)) {
            properties = (Map<?, ?>) ListUtil.first(arguments);
            children = ListUtil.rest(arguments);
        } else {
            properties = Collections.emptyMap();
            children = arguments;
        }
    }
}
