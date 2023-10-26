// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.autoscale.awsnodes;

import com.yahoo.config.provision.NodeType;

/**
 * Matches the internal repo implementation
 *
 * @author hakonhall
 * @author musum
 */
public class ReservedSpacePolicyImpl {

    public long getPartitionSizeInBase2Gb(NodeType nodeType, boolean sharedHost) {
        return new PartitionSizer(nodeType, sharedHost).getPartitionSize();
    }

    private static class PartitionSizer {

        private static final long imageCountForSharedHost = 6;
        private static final long imageCountForNonSharedHost = 3;

        // Add a buffer to allow a small increase in image size
        private static final long bufferSharedHost = 5;
        private static final long bufferNonSharedHost = 3;

        private final boolean sharedHost;

        PartitionSizer(NodeType nodeType, boolean sharedHost) {
            this.sharedHost = sharedHost;
        }

        long getPartitionSize() {
            return imageSize() * imageCount() + buffer();
        }

        private long imageSize() {
            return (long)7.7; // return (long)VespaContainerImage.maxImageSize(hostedSystem, nodeType);
        }

        private long buffer() {
            return sharedHost ? bufferSharedHost : bufferNonSharedHost;
        }

        private long imageCount() {
            return sharedHost ? imageCountForSharedHost : imageCountForNonSharedHost;
        }

    }

}
