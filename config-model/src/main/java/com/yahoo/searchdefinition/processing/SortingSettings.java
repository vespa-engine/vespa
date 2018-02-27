// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.processing;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.searchdefinition.RankProfileRegistry;
import com.yahoo.searchdefinition.document.Attribute;
import com.yahoo.searchdefinition.document.SDField;
import com.yahoo.searchdefinition.document.Sorting;
import com.yahoo.searchdefinition.Search;
import com.yahoo.vespa.model.container.search.QueryProfiles;

/**
 * Validate conflicting settings for sorting
 *
 * @author Vegard Havdal
 */
public class SortingSettings extends Processor {

    public SortingSettings(Search search, DeployLogger deployLogger, RankProfileRegistry rankProfileRegistry, QueryProfiles queryProfiles) {
        super(search, deployLogger, rankProfileRegistry, queryProfiles);
    }

    @Override
    public void process(boolean validate) {
        if ( ! validate) return;

        for (SDField field : search.allConcreteFields()) {
            for (Attribute attribute : field.getAttributes().values()) {
                Sorting sorting = attribute.getSorting();
                if (sorting.getFunction() != Sorting.Function.UCA) {
                    if (sorting.getStrength()!=null && sorting.getStrength() != Sorting.Strength.PRIMARY) {
                        warn(search, field, "Sort strength only works for sort function 'uca'.");
                    }
                    if (sorting.getLocale() != null && ! "".equals(sorting.getLocale())) {
                        warn(search, field, "Sort locale only works for sort function 'uca'.");
                    }
                }
            }
        }
    }

}
