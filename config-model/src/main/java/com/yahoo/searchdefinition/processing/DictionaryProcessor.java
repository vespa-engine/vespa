// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.processing;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.document.NumericDataType;
import com.yahoo.searchdefinition.RankProfileRegistry;
import com.yahoo.searchdefinition.Search;
import com.yahoo.searchdefinition.document.Attribute;
import com.yahoo.searchdefinition.document.Dictionary;
import com.yahoo.searchdefinition.document.SDField;
import com.yahoo.vespa.model.container.search.QueryProfiles;

/**
 * Propagates dictionary settings from field level to attribute level.
 * Only applies to numeric fields with fast-search enabled.
 *
 * @author baldersheim
 */
public class DictionaryProcessor extends Processor {
    public DictionaryProcessor(Search search, DeployLogger deployLogger, RankProfileRegistry rankProfileRegistry, QueryProfiles queryProfiles) {
        super(search, deployLogger, rankProfileRegistry, queryProfiles);
    }
    @Override
    public void process(boolean validate, boolean documentsOnly) {
        for (SDField field : search.allConcreteFields()) {
            Dictionary dictionary = field.getDictionary();
            if (dictionary == null) continue;

            Attribute attribute = field.getAttribute();
            if (attribute.getDataType() instanceof NumericDataType ) {
                if (attribute.isFastSearch()) {
                    attribute.setDictionary(dictionary);
                } else {
                    fail(search, field, "You must specify 'attribute:fast-search' to allow dictionary control");
                }
            } else {
                fail(search, field, "You can only specify 'dictionary:' for numeric fields");
            }
        }
    }
}
