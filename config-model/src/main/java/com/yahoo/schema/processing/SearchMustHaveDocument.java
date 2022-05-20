// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.processing;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.schema.RankProfileRegistry;
import com.yahoo.schema.Schema;
import com.yahoo.vespa.model.container.search.QueryProfiles;

/**
 * A search must have a document definition of the same name inside of it, otherwise crashes may occur as late as
 * during feeding
 *
 * @author Vegard Havdal
 */
public class SearchMustHaveDocument extends Processor {

    public SearchMustHaveDocument(Schema schema, DeployLogger deployLogger, RankProfileRegistry rankProfileRegistry, QueryProfiles queryProfiles) {
        super(schema, deployLogger, rankProfileRegistry, queryProfiles);
    }

    @Override
    public void process(boolean validate, boolean documentsOnly) {
        if ( ! validate) return;

        if (schema.getDocument() == null)
            throw new IllegalArgumentException("For " + schema +
                                               ": A search specification must have an equally named document inside of it.");
    }

}
