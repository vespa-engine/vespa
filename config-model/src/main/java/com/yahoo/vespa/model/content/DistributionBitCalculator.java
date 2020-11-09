// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.content;

import com.yahoo.vespa.model.content.cluster.ContentCluster;

public class DistributionBitCalculator {

    public static int getDistributionBits(int nodes, ContentCluster.DistributionMode mode) {
        if (mode == ContentCluster.DistributionMode.STRICT) {
            if (nodes < 5) {
                return 8; // Require few buckets in small clusters to ease testing
            } else if (nodes < 15) {
                return 16;
            } else if (nodes < 200) {
                return 21;
            } else if (nodes < 800) {
                return 25;
            } else if (nodes < 1500) {
                return 28;
            } else if (nodes < 5000) {
                return 30;
            } else {
                return 32;
            }
        } else if (mode == ContentCluster.DistributionMode.LOOSE) {
            if (nodes < 5) {
                return 8;
            } else if (nodes < 200) {
                return 16;
            } else {
                return 24;
            }
        } else if (mode == ContentCluster.DistributionMode.LEGACY) {
            if (nodes <= 2) {             // min 128 buckets/node
                return 8;
            } else if (nodes <= 6) {      // min 5462 buckets/node
                return 14;
            } else if (nodes <= 8) {      // min 9362 buckets/node
                return 16;
            } else if (nodes <= 10) {     // min 14563 buckets/node
                return 17;
            } else if (nodes <= 12) {     // min 23832 buckets/node
                return 18;
            } else if (nodes <= 20) {     // min 40329 buckets/node
                return 19;
            } else if (nodes <= 32) {     // min 49933 buckets/node
                return 20;
            } else if (nodes <= 64) {     // min 63550 buckets/node
                return 21;
            } else if (nodes <= 100) {    // min 64528 buckets/node
                return 22;
            } else if (nodes <= 256) {    // min 83056 buckets/node
                return 23;
            } else if (nodes <= 350) {    // min 65281 buckets/node
                return 24;
            } else if (nodes <= 500) {    // min 95597 buckets/node
                return 25;
            } else if (nodes <= 1024) {   // min 133950 buckets/node
                return 26;
            } else if (nodes <= 2048) {   // min 130944 buckets/node
                return 27;
            } else if (nodes <= 4096) {   // min 130008 buckets/node
                return 28;
            } else if (nodes <= 8192) {   // min 131040 buckets/node
                return 29;
            } else if (nodes <= 16384) {   // min 131056 buckets/node
                return 30;
            } else if (nodes <= 32768) {   // min 131064 buckets/node
                return 31;
            } else {                       // min 131068 buckets/node
                return 32;
            }
        } else {
            throw new IllegalArgumentException("We don't know how to handle mode " + mode);
        }
    }
}
