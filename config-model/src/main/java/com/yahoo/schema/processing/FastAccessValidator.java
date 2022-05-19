// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.processing;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.schema.RankProfileRegistry;
import com.yahoo.schema.Schema;
import com.yahoo.schema.document.Attribute;
import com.yahoo.vespa.model.container.search.QueryProfiles;

import java.util.stream.Collectors;

/**
 * Validates the use of the fast-access property.
 *
 * @author bjorncs
 */
public class FastAccessValidator extends Processor {

    public FastAccessValidator(Schema schema, DeployLogger deployLogger, RankProfileRegistry rankProfileRegistry, QueryProfiles queryProfiles) {
        super(schema, deployLogger, rankProfileRegistry, queryProfiles);
    }

    @Override
    public void process(boolean validate, boolean documentsOnly) {
        if ( ! validate) return;

        String invalidAttributes = schema.allFields()
                                         .flatMap(field -> field.getAttributes().values().stream())
                                         .filter(FastAccessValidator::isIncompatibleAttribute)
                                         .map(Attribute::getName)
                                         .collect(Collectors.joining(", "));
        if ( ! invalidAttributes.isEmpty()) {
            throw new IllegalArgumentException(
                            "For " + schema + ": The following attributes have a type that is incompatible with fast-access: " +
                            invalidAttributes + ". Predicate, tensor and reference attributes are incompatible with fast-access.");
        }
    }

    private static boolean isIncompatibleAttribute(Attribute attribute) {
        return attribute.isFastAccess() && isTypeIncompatibleWithFastAccess(attribute.getType());
    }

    private static boolean isTypeIncompatibleWithFastAccess(Attribute.Type type) {
        switch (type) {
            case PREDICATE:
            case TENSOR:
            case REFERENCE:
                return true;
            default:
                return false;
        }
    }

}
