// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespasignificance;


/**
 * This class contains the program parameters.
 *
 * @author MariusArhaug
 */
public class ClientParameters {
    // Show help page if true
    public final boolean help;

    // Input file for the program
    public final String inputFile;

    // Output file for the program
    public final String outputFile;

    // Field for the program
    public final String field;

    // Language for the program
    public final String language;

    // Document type identifier
    public final String docType;

    // Zstandard compression
    public final boolean zstCompression;

    public ClientParameters(
            boolean help,
            String inputFile,
            String outputFile,
            String field,
            String language,
            String docType,
            boolean zstCompression) {
        this.help = help;
        this.inputFile = inputFile;
        this.outputFile = outputFile;
        this.field = field;
        this.language = language;
        this.docType = docType;
        this.zstCompression = zstCompression;
    }

    public static class Builder {
        private boolean help;
        private String inputFile;
        private String outputFile;
        private String field;
        private String language;
        private String docType;
        private boolean zstCompression;

        public Builder setHelp(boolean help) {
            this.help = help;
            return this;
        }

        public Builder setInputFile(String inputFile) {
            this.inputFile = inputFile;
            return this;
        }

        public Builder setOutputFile(String outputFile) {
            this.outputFile = outputFile;
            return this;
        }

        public Builder setField(String field) {
            this.field = field;
            return this;
        }
        public Builder setLanguage(String language) {
            this.language = language;
            return this;
        }

        public Builder setDocType(String docType) {
            this.docType = docType;
            return this;
        }

        public Builder setZstCompression(String useZstCompression) {
            this.zstCompression = Boolean.parseBoolean(useZstCompression);
            return this;
        }

        public ClientParameters build() {
            return new ClientParameters(help, inputFile, outputFile, field, language, docType, zstCompression);
        }
    }
}
