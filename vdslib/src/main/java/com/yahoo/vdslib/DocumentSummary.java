// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vdslib;

import com.yahoo.vespa.objects.BufferSerializer;
import com.yahoo.vespa.objects.Deserializer;

import java.nio.ByteOrder;
import java.io.UnsupportedEncodingException;

public class DocumentSummary {

    private final Summary[] summaries;

    public DocumentSummary(Deserializer buf) {
        BufferSerializer bser = (BufferSerializer) buf; // This is a trick. This should be done in a different way.
        bser.order(ByteOrder.BIG_ENDIAN);
        buf.getInt(null); // legacy - ignored
        int numSummaries = buf.getInt(null);
        summaries = new Summary[numSummaries];
        if (numSummaries > 0) {
            int summaryBufferSize = buf.getInt(null);

            byte[] cArr = bser.getBuf().array();
            int start = bser.getBuf().arrayOffset() + bser.position();
            bser.position(bser.position() + summaryBufferSize);
            for(int i=0; i < numSummaries; i++) {
                int summarySize = buf.getInt(null);
                int end = start;
                while (cArr[end++] != 0);
                try {
                    byte [] sb = new byte [summarySize];
                    System.arraycopy(cArr, end, sb, 0, summarySize);
                    summaries[i] = new Summary(new String(cArr, start, end-start-1, "utf-8"), sb);
                    start = end + summarySize;
                } catch (UnsupportedEncodingException e) {
                    throw new RuntimeException("UTF-8 apparently not supported");
                }
            }
        }
    }

    /** Constructs a new message from a byte buffer. */
    public DocumentSummary(byte[] buffer) {
        this(BufferSerializer.wrap(buffer));
    }

    final public int getSummaryCount()         { return summaries.length; }
    final public Summary getSummary(int hitNo) { return summaries[hitNo]; }

    public static class Summary implements Comparable<Summary> {

        private final String  docId;
        private byte[] summary;

        private Summary(String docId) {
            this.docId = docId;
        }

        public Summary(String docId, byte [] summary) {
            this(docId);
            this.summary = summary;
        }

        final public String getDocId()           { return docId; }
        final public byte [] getSummary()        { return summary; }
        final public void setSummary(byte [] summary) { this.summary = summary; }

        public int compareTo(Summary s) {
            return getDocId().compareTo(s.getDocId());
        }

    }

}
