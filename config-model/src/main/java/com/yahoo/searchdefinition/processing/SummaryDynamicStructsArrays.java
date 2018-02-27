// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.processing;


import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.searchdefinition.RankProfileRegistry;
import com.yahoo.document.*;
import com.yahoo.searchdefinition.Search;
import com.yahoo.searchdefinition.document.SDField;
import com.yahoo.vespa.documentmodel.SummaryField;
import com.yahoo.vespa.documentmodel.SummaryTransform;
import com.yahoo.vespa.model.container.search.QueryProfiles;

/**
 * Fail if:
 * An SD field explicitly says summary:dynamic , but the field is wset, array or struct.
 * If there is an explicitly defined summary class, saying dynamic in one of its summary
 * fields is always legal.
 *
 * @author Vegard Havdal
 */
public class SummaryDynamicStructsArrays extends Processor {

    public SummaryDynamicStructsArrays(Search search, DeployLogger deployLogger, RankProfileRegistry rankProfileRegistry, QueryProfiles queryProfiles) {
        super(search, deployLogger, rankProfileRegistry, queryProfiles);
    }

    @Override
    public void process(boolean validate) {
        if ( ! validate) return;

        for (SDField field : search.allConcreteFields()) {
            DataType type = field.getDataType();
            if (type instanceof ArrayDataType || type instanceof WeightedSetDataType || type instanceof StructDataType) {
                for (SummaryField sField : field.getSummaryFields()) {
                    if (sField.getTransform().equals(SummaryTransform.DYNAMICTEASER)) {
                        throw new IllegalArgumentException("For field '"+field.getName()+"': dynamic summary is illegal " +
                                                           "for fields of type struct, array or weighted set. Use an " +
                                                           "explicit summary class with explicit summary fields sourcing" +
                                                           " from the array/struct/weighted set.");
                    }
                }
            }
        }
    }

}
