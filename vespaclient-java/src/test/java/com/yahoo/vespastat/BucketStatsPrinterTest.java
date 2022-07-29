// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespastat;

import com.yahoo.document.BucketId;
import com.yahoo.documentapi.messagebus.protocol.GetBucketListReply;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class BucketStatsPrinterTest {

    private BucketStatsRetriever retriever;
    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final String bucketSpace = "default";

    @BeforeEach
    public void mockBucketStatsRetriever() throws BucketStatsException {
        retriever = mock(BucketStatsRetriever.class);
        when(retriever.getBucketIdForType(any(), any())).thenReturn(new BucketId(0x42));
        when(retriever.retrieveBucketList(any(), any())).thenReturn(Collections.emptyList());
        when(retriever.retrieveBucketStats(any(), any(), any(), any())).thenReturn("");
    }

    @AfterEach
    public void resetOutputMock() {
        out.reset();
    }

    private String getOutputString() {
        String content = out.toString();
        out.reset();
        return content;
    }

    private String retreiveAndPrintBucketStats(ClientParameters.SelectionType type, String id, boolean dumpData) throws BucketStatsException {
        BucketStatsPrinter printer = new BucketStatsPrinter(retriever, new PrintStream(out));
        printer.retrieveAndPrintBucketStats(type, id, dumpData, bucketSpace);
        return getOutputString();
    }

    @Test
    void testShouldPrintBucketIdForUserAndGroup() throws BucketStatsException {
        String output = retreiveAndPrintBucketStats(ClientParameters.SelectionType.USER, "1234", false);
        assertTrue(output.contains("Generated 32-bit bucket id"));

        output = retreiveAndPrintBucketStats(ClientParameters.SelectionType.GROUP, "mygroup", false);
        assertTrue(output.contains("Generated 32-bit bucket id"));
    }

    @Test
    void testShouldPrintWarningIfBucketListEmpty() throws BucketStatsException {
        String output = retreiveAndPrintBucketStats(ClientParameters.SelectionType.USER, "1234", false);
        assertTrue(output.contains("No actual files were stored for this bucket"));
    }

    @Test
    void testShouldPrintBucketList() throws BucketStatsException {
        List<GetBucketListReply.BucketInfo> bucketList = new ArrayList<>();
        String dummyInfoString = "dummyinformation";
        bucketList.add(new GetBucketListReply.BucketInfo(new BucketId(0), dummyInfoString));
        when(retriever.retrieveBucketList(any(), any())).thenReturn(bucketList);

        String output = retreiveAndPrintBucketStats(ClientParameters.SelectionType.USER, "1234", false);
        assertTrue(output.contains(dummyInfoString));
    }

    @Test
    void testShouldPrintBucketStats() throws BucketStatsException {
        String dummyBucketStats = "dummystats";
        GetBucketListReply.BucketInfo bucketInfo = new GetBucketListReply.BucketInfo(new BucketId(0), "dummy");
        when(retriever.retrieveBucketList(any(), any())).thenReturn(Collections.singletonList(bucketInfo));
        when(retriever.retrieveBucketStats(any(), any(), any(), any())).thenReturn(dummyBucketStats);

        String output = retreiveAndPrintBucketStats(ClientParameters.SelectionType.USER, "1234", true);
        assertTrue(output.contains(dummyBucketStats));
    }
}
