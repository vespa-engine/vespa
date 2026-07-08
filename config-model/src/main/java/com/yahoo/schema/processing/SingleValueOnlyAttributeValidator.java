// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.processing;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.schema.RankProfileRegistry;
import com.yahoo.schema.Schema;
import com.yahoo.schema.document.Attribute;
import com.yahoo.vespa.model.container.search.QueryProfiles;

/**
 * Validates attribute fields using raw type, ensuring the collection type is single value.
 *
 * @author geirst
 */
public class SingleValueOnlyAttributeValidator extends Processor {

    public SingleValueOnlyAttributeValidator(Schema schema, DeployLogger deployLogger, RankProfileRegistry rankProfileRegistry, QueryProfiles queryProfiles) {
        super(schema, deployLogger, rankProfileRegistry, queryProfiles);
    }

    @Override
    public void process(boolean validate, boolean documentsOnly) {
        for (var field : schema.allConcreteFields()) {
            var attribute = field.getAttribute();
            if (attribute == null) {
                continue;
            }
            if (attribute.getType().equals(Attribute.Type.RAW) &&
                    !attribute.getCollectionType().equals(Attribute.CollectionType.SINGLE)) {
                fail(schema, field, "Only single value " + attribute.getType().getName() + " attribute fields are supported");
            }
        }
    }
}
