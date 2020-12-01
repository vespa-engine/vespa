// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.configchange;

/**
 * Class used to format re-feed actions for human readability.
 *
 * @author geirst
 * @since 5.44
 */
public class RefeedActionsFormatter {

    private final RefeedActions actions;

    public RefeedActionsFormatter(RefeedActions actions) {
        this.actions = actions;
    }

    public String format() {
        StringBuilder builder = new StringBuilder();
        for (RefeedActions.Entry entry : actions.getEntries()) {
            builder.append(entry.name() + ": Consider removing data and re-feed document type '" + entry.getDocumentType() +
                           "' in cluster '" + entry.getClusterName() + "' because:\n");
            int counter = 1;
            for (String message : entry.getMessages()) {
                builder.append("    " + (counter++) + ") " + message + "\n");
            }
        }
        return builder.toString();
    }

}
