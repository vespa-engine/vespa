// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.processing;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.searchdefinition.RankProfileRegistry;
import com.yahoo.document.*;
import com.yahoo.searchdefinition.Search;
import com.yahoo.searchdefinition.document.SDField;
import com.yahoo.searchdefinition.document.Stemming;
import com.yahoo.vespa.model.container.search.QueryProfiles;

import java.util.Arrays;
import java.util.List;

/**
 * @author baldersheim
 */
public class UriHack extends Processor {

    private static final List<String> URL_SUFFIX =
            Arrays.asList("scheme", "host", "port", "path", "query", "fragment", "hostname");

    public UriHack(Search search,
                   DeployLogger deployLogger,
                   RankProfileRegistry rankProfileRegistry,
                   QueryProfiles queryProfiles) {
        super(search, deployLogger, rankProfileRegistry, queryProfiles);
    }

    @Override
    public void process(boolean validate) {
        for (SDField field : search.allConcreteFields()) {
            if (field.doesIndexing()) {
                DataType fieldType = field.getDataType();
                if (fieldType instanceof CollectionDataType) {
                    fieldType = ((CollectionDataType)fieldType).getNestedType();
                }
                if (fieldType == DataType.URI) {
                    processField(search, field);
                }
            }
        }
    }

    private void processField(Search search, SDField uriField) {
        String uriName = uriField.getName();
        uriField.setStemming(Stemming.NONE);
        DataType generatedType = DataType.STRING;
        if (uriField.getDataType() instanceof ArrayDataType) {
            generatedType = new ArrayDataType(DataType.STRING);
        }
        else if (uriField.getDataType() instanceof WeightedSetDataType) {
            WeightedSetDataType wdt = (WeightedSetDataType) uriField.getDataType();
            generatedType = new WeightedSetDataType(DataType.STRING, wdt.createIfNonExistent(), wdt.removeIfZero());
        }

        for (String suffix : URL_SUFFIX) {
            String partName = uriName + "." + suffix;
            // I wonder if this is explicit in qrs or implicit in backend?
            // search.addFieldSetItem(uriName, partName);
            SDField partField = new SDField(partName, generatedType, true);
            partField.setIndexStructureField(uriField.doesIndexing());
            partField.setRankType(uriField.getRankType());
            partField.setStemming(Stemming.NONE);
            partField.getNormalizing().inferLowercase();
            if (uriField.getIndex(suffix) != null) {
                partField.addIndex(uriField.getIndex(suffix));
            }
            search.addExtraField(partField);
            search.fieldSets().addBuiltInFieldSetItem(BuiltInFieldSets.INTERNAL_FIELDSET_NAME, partField.getName());
        }
    }

}
