// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.processing;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.document.Field;
import com.yahoo.searchdefinition.RankProfileRegistry;
import com.yahoo.searchdefinition.Search;
import com.yahoo.searchdefinition.document.Attribute;
import com.yahoo.tensor.TensorType;
import com.yahoo.vespa.model.container.search.QueryProfiles;

/**
 * Validates the 'paged' attribute setting and throws if specified on unsupported types.
 *
 * @author geirst
 */
public class PagedAttributeValidator extends Processor {


    public PagedAttributeValidator(Search search,
                                   DeployLogger deployLogger,
                                   RankProfileRegistry rankProfileRegistry,
                                   QueryProfiles queryProfiles) {
        super(search, deployLogger, rankProfileRegistry, queryProfiles);
    }

    @Override
    public void process(boolean validate, boolean documentsOnly) {
        if (!validate) {
            return;
        }
        for (var field : search.allConcreteFields()) {
            for (var attribute : field.getAttributes().values()) {
                if (attribute.isPaged()) {
                    validatePagedSetting(field, attribute);
                }
            }
        }
    }

    private void validatePagedSetting(Field field, Attribute attribute) {
        var tensorType = attribute.tensorType();
        if (!tensorType.isPresent() ||
                !isDenseTensorType(tensorType.get())) {
            fail(search, field, "The 'paged' attribute setting is only supported for dense tensor types");
        }
    }

    private boolean isDenseTensorType(TensorType type) {
        return type.dimensions().stream().allMatch(d -> d.isIndexed());
    }
}
