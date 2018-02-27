// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.processing;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.searchdefinition.RankProfileRegistry;
import com.yahoo.searchdefinition.Search;
import com.yahoo.searchdefinition.document.Attribute;
import com.yahoo.vespa.model.container.search.QueryProfiles;

import java.util.stream.Collectors;

/**
 * Validates the use of the fast-access property.
 *
 * @author bjorncs
 */
public class FastAccessValidator extends Processor {

    public FastAccessValidator(Search search, DeployLogger deployLogger, RankProfileRegistry rankProfileRegistry, QueryProfiles queryProfiles) {
        super(search, deployLogger, rankProfileRegistry, queryProfiles);
    }

    @Override
    public void process(boolean validate) {
        if ( ! validate) return;

        String invalidAttributes = search.allFields()
                .flatMap(field -> field.getAttributes().values().stream())
                .filter(FastAccessValidator::isIncompatibleAttribute)
                .map(Attribute::getName)
                .collect(Collectors.joining(", "));
        if (!invalidAttributes.isEmpty()) {
            throw new IllegalArgumentException(
                    String.format(
                            "For search '%s': The following attributes have a type that is incompatible with fast-access: %s. " +
                                    "Predicate, tensor and reference attributes are incompatible with fast-access.",
                            search.getName(),
                            invalidAttributes));
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
