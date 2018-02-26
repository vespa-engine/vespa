// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.processing;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.searchdefinition.RankProfileRegistry;
import com.yahoo.searchdefinition.document.SDDocumentType;
import com.yahoo.searchdefinition.Search;
import com.yahoo.vespa.model.container.search.QueryProfiles;

import java.util.HashSet;
import java.util.Set;

/**
 * @author Simon Thoresen
 */
public class ReservedDocumentNames extends Processor {

    private static final Set<String> RESERVED_NAMES = new HashSet<>();

    static {
        for (SDDocumentType dataType : SDDocumentType.VESPA_DOCUMENT.getTypes()) {
            RESERVED_NAMES.add(dataType.getName());
        }
    }

    public ReservedDocumentNames(Search search, DeployLogger deployLogger, RankProfileRegistry rankProfileRegistry, QueryProfiles queryProfiles) {
        super(search, deployLogger, rankProfileRegistry, queryProfiles);
    }

    @Override
    public void process(boolean validate) {
        if ( ! validate) return;

        String docName = search.getDocument().getName();
        if (RESERVED_NAMES.contains(docName)) {
            throw new IllegalArgumentException("For search '" + search.getName() + "': Document name '" + docName +
                                               "' is reserved.");
        }
    }
}
