// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.processing.multifieldresolver;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.schema.document.SDField;
import com.yahoo.schema.Schema;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;

/**
 * Resolver-class for harmonizing index-commands in multifield indexes
 */
public class IndexCommandResolver extends MultiFieldResolver {

    /** Commands which don't have to be harmonized between fields */
    private static List<String> ignoredCommands = new ArrayList<>();

    /** Commands which must be harmonized between fields */
    private static List<String> harmonizedCommands = new ArrayList<>();

    static {
        String[] ignore = { "complete-boost", "literal-boost", "highlight" };
        ignoredCommands.addAll(Arrays.asList(ignore));
        String[] harmonize = { "stemming", "normalizing" };
        harmonizedCommands.addAll(Arrays.asList(harmonize));
    }

    public IndexCommandResolver(String indexName, List<SDField> fields, Schema schema, DeployLogger logger) {
        super(indexName, fields, schema, logger);
    }

    /**
     * Check index-commands for each field, report and attempt to fix any
     * inconsistencies
     */
    public void resolve() {
        for (SDField field : fields) {
            for (String command : field.getQueryCommands()) {
                if (!ignoredCommands.contains(command))
                    checkCommand(command);
            }
        }
    }

    private void checkCommand(String command) {
        for (SDField field : fields) {
            if (!field.hasQueryCommand(command)) {
                if (harmonizedCommands.contains(command)) {
                    deployLogger.logApplicationPackage(Level.WARNING, command + " must be added to all fields going to the same index (" + indexName + ")" +
                            ", adding to field " + field.getName());
                    field.addQueryCommand(command);
                } else {
                    deployLogger.logApplicationPackage(Level.WARNING, "All fields going to the same index should have the same query-commands. Field \'" + field.getName() +
                            "\' doesn't contain command \'" + command+"\'");
                }
            }
        }
    }
}
