// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.vespasignificance.export;

import java.util.Optional;

/**
 * This class contains the program parameters for export subcommand.
 *
 * @author johsol
 */
public record ExportClientParameters(String indexDir, String outputFile, String fieldName,
                                     Optional<String> schemaName, Optional<String> clusterName, Optional<String> nodeIndex) {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String indexDir;
        private String outputFile;
        private String fieldName;
        private String clusterName;
        private String schemaName;
        private String nodeIndex;

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

        public Builder nodeIndex(String value) {
            this.nodeIndex = value;
            return this;
        }

        public ExportClientParameters build() {
            return new ExportClientParameters(indexDir, outputFile, fieldName, Optional.ofNullable(schemaName), Optional.ofNullable(clusterName),  Optional.ofNullable(nodeIndex));
        }
    }

}
