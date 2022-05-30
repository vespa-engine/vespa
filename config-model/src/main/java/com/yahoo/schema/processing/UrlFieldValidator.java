// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.processing;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.document.DataType;
import com.yahoo.schema.RankProfileRegistry;
import com.yahoo.schema.Schema;
import com.yahoo.schema.document.ImmutableSDField;
import com.yahoo.vespa.model.container.search.QueryProfiles;

/**
 * @author bratseth
 */
public class UrlFieldValidator extends Processor {

    public UrlFieldValidator(Schema schema, DeployLogger deployLogger, RankProfileRegistry rankProfileRegistry, QueryProfiles queryProfiles) {
        super(schema, deployLogger, rankProfileRegistry, queryProfiles);
    }

    @Override
    public void process(boolean validate, boolean documentsOnly) {
        if ( ! validate) return;

        for (ImmutableSDField field : schema.allConcreteFields()) {
            if  ( ! field.getDataType().equals(DataType.URI)) continue;

            if (field.doesAttributing())
                throw new IllegalArgumentException("Error in " + field + " in " + schema + ": " +
                                                   "uri type fields cannot be attributes");
        }

    }

}
