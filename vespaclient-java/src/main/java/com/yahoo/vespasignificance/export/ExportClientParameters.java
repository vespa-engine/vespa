// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespasignificance.export;

import java.util.Optional;

/**
 * This class contains the program parameters for export subcommand.
 *
 * @author johsol
 */
public record ExportClientParameters(boolean locateIndex, String indexDir, String outputFile, String fieldName,
                                     Optional<String> schemaName, Optional<String> clusterName) {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private boolean locateIndex;
        private String indexDir;
        private String outputFile = "out.txt";
        private String fieldName;
        private String clusterName;
        private String schemaName;

        public Builder locateIndex(boolean value) {
            this.locateIndex = value;
            return this;
        }

        public Builder indexDir(String value) {
            this.indexDir = value;
            return this;
        }

        public Builder outputFile(String value) {
            this.outputFile = value;
            return this;
        }

        public Builder fieldName(String value) {
            this.fieldName = value;
            return this;
        }

        public Builder schemaName(String value) {
            this.schemaName = value;
            return this;
        }

        public Builder clusterName(String value) {
            this.clusterName = value;
            return this;
        }

        public ExportClientParameters build() {
            return new ExportClientParameters(locateIndex, indexDir, outputFile, fieldName, Optional.ofNullable(schemaName), Optional.ofNullable(clusterName));
        }
    }

}
