// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespastat;

import com.yahoo.document.FixedBucketSpaces;

/**
 * This class contains the program parameters.
 *
 * @author bjorncs
 */
public class ClientParameters {
    // Show help page if true
    public final boolean help;
    // Dump list of documents for all buckets matching the selection if true
    public final boolean dumpData;
    // The message bus route
    public final String route;
    // The selection type
    public final SelectionType selectionType;
    // The selection id
    public final String id;
    public final String bucketSpace;

    public ClientParameters(
            boolean help,
            boolean dumpData,
            String route,
            SelectionType selectionType,
            String id) {
        this(help, dumpData, route, selectionType, id, FixedBucketSpaces.defaultSpace());
    }

    public ClientParameters(
            boolean help,
            boolean dumpData,
            String route,
            SelectionType selectionType,
            String id,
            String bucketSpace) {
        this.help = help;
        this.dumpData = dumpData;
        this.route = route;
        this.selectionType = selectionType;
        this.id = id;
        this.bucketSpace = bucketSpace;
    }

    public enum SelectionType {USER, GROUP, BUCKET, GID, DOCUMENT}

    public static class Builder {
        private boolean help;
        private boolean dumpData;
        private String route;
        private SelectionType selectionType;
        private String id;
        private String bucketSpace = FixedBucketSpaces.defaultSpace();

        public Builder setHelp(boolean help) {
            this.help = help;
            return this;
        }

        public Builder setDumpData(boolean dumpData) {
            this.dumpData = dumpData;
            return this;
        }

        public Builder setRoute(String route) {
            this.route = route;
            return this;
        }

        public Builder setSelectionType(SelectionType selectionType) {
            this.selectionType = selectionType;
            return this;
        }

        public Builder setId(String id) {
            this.id = id;
            return this;
        }

        public Builder setBucketSpace(String bucketSpace) {
            this.bucketSpace = bucketSpace;
            return this;
        }

        public ClientParameters build() {
            return new ClientParameters(help, dumpData, route, selectionType, id, bucketSpace);
        }
    }

}
