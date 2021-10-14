// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.processing;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.searchdefinition.RankProfileRegistry;
import com.yahoo.searchdefinition.Search;
import com.yahoo.searchdefinition.document.Attribute;
import com.yahoo.vespa.model.container.search.QueryProfiles;

/**
 * Validates attribute fields using bool type, ensuring the collection type is supported.
 *
 * Currently, only the single value bool type is supported.
 *
 * @author geirst
 */
public class BoolAttributeValidator extends Processor {

    public BoolAttributeValidator(Search search, DeployLogger deployLogger, RankProfileRegistry rankProfileRegistry, QueryProfiles queryProfiles) {
        super(search, deployLogger, rankProfileRegistry, queryProfiles);
    }

    @Override
    public void process(boolean validate, boolean documentsOnly) {
        for (var field : search.allConcreteFields()) {
            var attribute = field.getAttribute();
            if (attribute == null) {
                continue;
            }
            if (attribute.getType().equals(Attribute.Type.BOOL) &&
                    !attribute.getCollectionType().equals(Attribute.CollectionType.SINGLE)) {
                fail(search, field, "Only single value bool attribute fields are supported");
            }
        }
    }
}
