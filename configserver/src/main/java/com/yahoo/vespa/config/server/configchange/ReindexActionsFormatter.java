// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.configchange;

/**
 * Class used to format re-index actions for human readability.
 *
 * @author bjorncs
 */
class ReindexActionsFormatter {

    private final ReindexActions actions;

    ReindexActionsFormatter(ReindexActions actions) {
        this.actions = actions;
    }

    String format() {
        StringBuilder builder = new StringBuilder();
        for (ReindexActions.Entry entry : actions.getEntries()) {
            builder.append(entry.name() + ": Consider re-indexing document type '" + entry.getDocumentType() +
                           "' in cluster '" + entry.getClusterName() + "' because:\n");
            int counter = 1;
            for (String message : entry.getMessages()) {
                builder.append("    " + (counter++) + ") " + message + "\n");
            }
        }
        return builder.toString();
    }

}
