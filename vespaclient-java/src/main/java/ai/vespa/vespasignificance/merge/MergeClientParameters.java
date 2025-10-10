// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.vespasignificance.merge;

import java.util.ArrayList;
import java.util.List;

/**
 * This class contains the program parameters for merge subcommand.
 *
 * @author johsol
 */
public record MergeClientParameters(
        String outputFile,
        List<String> inputFiles,
        long minKeep,
        boolean zstCompress) {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String outputFile;
        private final List<String> inputFiles = new ArrayList<>();
        private long minKeep = Long.MIN_VALUE;
        private boolean zstCompress = false;

        public Builder outputFile(String value) {
            this.outputFile = value;
            return this;
        }

        public Builder addInputFile(String file) {
            this.inputFiles.add(file);
            return this;
        }

        public Builder addInputFiles(List<String> files) {
            files.forEach(this::addInputFile);
            return this;
        }

        public Builder minKeep(long minKeep) {
            this.minKeep = minKeep;
            return this;
        }

        public Builder zstCompress(boolean value) {
            this.zstCompress = value;
            return this;
        }

        public MergeClientParameters build() {
            return new MergeClientParameters(outputFile, inputFiles, minKeep, zstCompress);
        }
    }

}
