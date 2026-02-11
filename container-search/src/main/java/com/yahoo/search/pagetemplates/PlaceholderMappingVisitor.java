// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.pagetemplates;

import com.yahoo.search.pagetemplates.model.*;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Creates a map from placeholder id to the choice providing its value
 * for all placeholder values visited.
 * <p>
 * This visitor will throw an IllegalArgumentException if the same placeholder id
 * is referenced by two choices.
 *
 * @author bratseth
 */
class PlaceholderMappingVisitor extends PageTemplateVisitor {

    private final Map<String, MapChoice> placeholderIdToChoice = new LinkedHashMap<>();

    @Override
    public void visit(MapChoice mapChoice) {
        List<String> placeholderIds = mapChoice.placeholderIds();
        for (String placeholderId : placeholderIds) {
            MapChoice existingChoice = placeholderIdToChoice.put(placeholderId,mapChoice);
            if (existingChoice != null)
                throw new IllegalArgumentException("placeholder id '" + placeholderId + "' is referenced by both " +
                                                   mapChoice + " and " + existingChoice + ": Only one reference is allowed");
        }
    }

    public Map<String, MapChoice> getMap() { return placeholderIdToChoice; }

}
