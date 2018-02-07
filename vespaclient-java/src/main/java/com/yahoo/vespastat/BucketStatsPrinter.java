// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespastat;

import com.yahoo.document.BucketId;
import com.yahoo.documentapi.messagebus.protocol.GetBucketListReply;

import java.io.PrintStream;
import java.util.List;

/**
 * The class is responsible for printing bucket information to a printstream.
 *
 * @author bjorncs
 */
public class BucketStatsPrinter {
    private final BucketStatsRetriever retriever;
    private final PrintStream out;

    public BucketStatsPrinter(
            BucketStatsRetriever retriever,
            PrintStream out) {
        this.retriever = retriever;
        this.out = out;
    }

    public void retrieveAndPrintBucketStats(ClientParameters.SelectionType type, String id, boolean dumpData, String bucketSpace) throws BucketStatsException {
        BucketId bucketId = retriever.getBucketIdForType(type, id);
        if (type == ClientParameters.SelectionType.GROUP || type == ClientParameters.SelectionType.USER) {
            out.printf("Generated 32-bit bucket id: %s\n", bucketId);
        }

        List<GetBucketListReply.BucketInfo> bucketList = retriever.retrieveBucketList(bucketId, bucketSpace);
        printBucketList(bucketList);

        if (dumpData) {
            for (GetBucketListReply.BucketInfo bucketInfo : bucketList) {
                BucketId bucket = bucketInfo.getBucketId();
                String bucketStats = retriever.retrieveBucketStats(type, id, bucket, bucketSpace);
                printBucketStats(bucket, bucketStats);
            }
        }
    }

    private void printBucketList(List<GetBucketListReply.BucketInfo> bucketList) {
        if (bucketList.isEmpty()) {
            out.println("No actual files were stored for this bucket.");
        } else {
            out.println("Bucket maps to the following actual files:");
            for (GetBucketListReply.BucketInfo bucketInfo : bucketList) {
                out.printf("\t%s\n", bucketInfo);
            }
        }
    }

    private void printBucketStats(BucketId bucket, String stats) {
        out.printf("\nDetails for %s:\n%s", bucket, stats);
    }

}
