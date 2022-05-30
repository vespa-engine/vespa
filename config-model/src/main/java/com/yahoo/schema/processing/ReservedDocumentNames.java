// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.processing;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.schema.RankProfileRegistry;
import com.yahoo.schema.Schema;
import com.yahoo.schema.document.SDDocumentType;
import com.yahoo.vespa.model.container.search.QueryProfiles;

import java.util.HashSet;
import java.util.Set;

/**
 * @author Simon Thoresen Hult
 */
public class ReservedDocumentNames extends Processor {

    private static final Set<String> RESERVED_NAMES = new HashSet<>();

    static {
        for (SDDocumentType dataType : SDDocumentType.VESPA_DOCUMENT.getTypes()) {
            RESERVED_NAMES.add(dataType.getName());
        }
    }

    public ReservedDocumentNames(Schema schema, DeployLogger deployLogger, RankProfileRegistry rankProfileRegistry, QueryProfiles queryProfiles) {
        super(schema, deployLogger, rankProfileRegistry, queryProfiles);
    }

    @Override
    public void process(boolean validate, boolean documentsOnly) {
        if ( ! validate) return;

        String docName = schema.getDocument().getName();
        if (RESERVED_NAMES.contains(docName))
            throw new IllegalArgumentException("For " + schema + ": Document name '" + docName + "' is reserved.");
    }

}
