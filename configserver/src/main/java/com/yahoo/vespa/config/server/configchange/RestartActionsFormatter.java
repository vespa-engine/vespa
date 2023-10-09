// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.configchange;

/**
 * Class used to format restart actions for human readability.
 *
 * @author geirst
 */
public class RestartActionsFormatter {

    private final RestartActions actions;

    public RestartActionsFormatter(RestartActions actions) {
        this.actions = actions;
    }

    public String format() {
        StringBuilder builder = new StringBuilder();
        for (RestartActions.Entry entry : actions.getEntries()) {
            builder.append("In cluster '" + entry.getClusterName() + "' of type '" + entry.getClusterType() + "':\n");
            builder.append("    Restart services of type '" + entry.getServiceType() + "' because:\n");
            int counter = 1;
            for (String message : entry.getMessages()) {
                builder.append("        " + counter++ + ") " + message + "\n");
            }
        }
        return builder.toString();
    }
}
