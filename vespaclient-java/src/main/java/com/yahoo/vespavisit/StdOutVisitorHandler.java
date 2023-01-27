// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespavisit;

import com.yahoo.document.BucketId;
import com.yahoo.document.Document;
import com.yahoo.document.DocumentId;
import com.yahoo.document.json.JsonWriter;
import com.yahoo.document.serialization.XmlStream;
import com.yahoo.documentapi.AckToken;
import com.yahoo.documentapi.DumpVisitorDataHandler;
import com.yahoo.documentapi.VisitorDataHandler;
import com.yahoo.documentapi.messagebus.protocol.DocumentListEntry;
import com.yahoo.documentapi.messagebus.protocol.DocumentListMessage;
import com.yahoo.documentapi.messagebus.protocol.EmptyBucketsMessage;
import com.yahoo.documentapi.messagebus.protocol.MapVisitorMessage;
import java.util.logging.Level;
import com.yahoo.messagebus.Message;

import java.io.IOException;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * A visitor data and progress handler that writes to STDOUT.
 *
 * Due to java not being able to inherit two classes, and neither being an
 * interface this had to be implemented by creating a wrapper class.
 *
 * @author Thomas Gundersen
 */
@SuppressWarnings("deprecation")
public class StdOutVisitorHandler extends VdsVisitHandler {

    private static final Logger log = Logger.getLogger(
                                        StdOutVisitorHandler.class.getName());
    private final boolean printIds;
    private final boolean indentXml;
    private final int processTimeMilliSecs;
    private final PrintStream out;
    private final boolean jsonOutput;
    private final boolean tensorShortForm;
    private final boolean tensorDirectValues;

    private final VisitorDataHandler dataHandler;

    public StdOutVisitorHandler(boolean printIds, boolean indentXml,
                                boolean showProgress, boolean showStatistics, boolean doStatistics,
                                boolean abortOnClusterDown, int processtime, boolean jsonOutput,
                                boolean tensorShortForm,
                                boolean tensorDirectValues)
    {
        this(printIds, indentXml, showProgress, showStatistics, doStatistics, abortOnClusterDown, processtime,
             jsonOutput, tensorShortForm, tensorDirectValues, createStdOutPrintStream());
    }

    StdOutVisitorHandler(boolean printIds, boolean indentXml,
                         boolean showProgress, boolean showStatistics, boolean doStatistics,
                         boolean abortOnClusterDown, int processtime, boolean jsonOutput,
                         boolean tensorShortForm, boolean tensorDirectValues, PrintStream out)
    {
        super(showProgress, showStatistics, abortOnClusterDown);
        this.printIds = printIds;
        this.indentXml = indentXml;
        this.processTimeMilliSecs = processtime;
        this.jsonOutput = jsonOutput;
        this.tensorShortForm = tensorShortForm;
        this.tensorDirectValues = tensorDirectValues;
        this.out = out;
        this.dataHandler = new DataHandler(doStatistics);
    }

    private static PrintStream createStdOutPrintStream() {
        try {
            return new PrintStream(System.out, true, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e); // Will not happen - UTF-8 is always supported
        }
    }

    @Override
    public void onDone() {
    }

    public VisitorDataHandler getDataHandler() { return dataHandler; }

    class StatisticsMap extends LinkedHashMap<String, Integer> {
        int maxSize;

        StatisticsMap(int maxSize) {
            super(100, (float)0.75, true);
            this.maxSize = maxSize;
        }

        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Integer> eldest) {
            if (size() > maxSize) {
                dump(eldest);
                return true;
            }

            return false;
        }

        private void dump(Map.Entry<String, Integer> e) {
            out.println(e.getKey() + ":" + e.getValue());
        }

        public void dumpAll() {
            for (Map.Entry<String, Integer> e : entrySet()) {
                dump(e);
            }
            clear();
        }
    }

    class DataHandler extends DumpVisitorDataHandler {
        boolean doStatistics;
        StatisticsMap statisticsMap = new StatisticsMap(10000);
        private volatile boolean first = true;

        public DataHandler(boolean doStatistics) {
            this.doStatistics = doStatistics;
        }

        @Override
        public void onMessage(Message m, AckToken token) {
            if (processTimeMilliSecs > 0) {
                try {
                    Thread.sleep(processTimeMilliSecs);
                } catch (InterruptedException e) {}
            }

            synchronized (printLock) {
                if (m instanceof MapVisitorMessage) {
                    onMapVisitorData(((MapVisitorMessage)m).getData());
                    ack(token);
                } else if (m instanceof DocumentListMessage) {
                    DocumentListMessage dlm = (DocumentListMessage)m;
                    onDocumentList(dlm.getBucketId(), dlm.getDocuments());
                    ack(token);
                } else if (m instanceof EmptyBucketsMessage) {
                    onEmptyBuckets(((EmptyBucketsMessage)m).getBucketIds());
                    ack(token);
                } else {
                    super.onMessage(m, token);
                }
            }
        }

        @Override
        public void onDocument(Document doc, long timestamp) {
            try {
                if (lastLineIsProgress) {
                    System.err.print('\r');
                }

                if (printIds) {
                    out.print(doc.getId());
                    out.print(" (Last modified at ");
                    out.println(timestamp + ")");
                } else {
                    if (jsonOutput) {
                        writeJsonDocument(doc);
                    } else {
                        out.print(doc.toXML(
                                indentXml ? "  " : ""));
                    }
                }
            } catch (Exception e) {
                System.err.println("Failed to output document: "
                        + e.getMessage());
                getControlHandler().abort();
            }
        }

        private void writeJsonDocument(Document doc) throws IOException {
            writeFeedStartOrRecordSeparator();
            out.write(JsonWriter.toByteArray(doc, tensorShortForm, tensorDirectValues));
        }

        @Override
        public void onRemove(DocumentId docId) {
            try {
                if (lastLineIsProgress) {
                    System.err.print('\r');
                }

                if (printIds) {
                    out.println(docId + " (Removed)");
                } else {
                    if (jsonOutput) {
                        writeJsonDocumentRemove(docId);
                    } else {
                        XmlStream stream = new XmlStream();
                        stream.beginTag("remove");
                        stream.addAttribute("documentid", docId);
                        stream.endTag();
                        assert(stream.isFinalized());
                        out.print(stream);
                    }
                }
            } catch (Exception e) {
                System.err.println("Failed to output document: "
                        + e.getMessage());
                getControlHandler().abort();
            }
        }

        private void writeJsonDocumentRemove(DocumentId docId)
                throws IOException {
            writeFeedStartOrRecordSeparator();
            out.write(JsonWriter.documentRemove(docId));
        }

        private void writeFeedStartOrRecordSeparator() {
            if (first) {
                out.println("[");
                first = false;
            } else {
                out.println(",");
            }
        }

        private void onMapVisitorData(Map<String, String> data) {
            for (String key : data.keySet()) {
                if (doStatistics) {
                    Integer i = statisticsMap.get(key);
                    if (i != null) {
                        statisticsMap.put(key, Integer.parseInt(data.get(key)) + i);
                    } else {
                        statisticsMap.put(key, Integer.parseInt(data.get(key)));
                    }
                } else {
                    out.println(key + ":" + data.get(key));
                }
            }
        }

        private void onDocumentList(BucketId bucketId, List<DocumentListEntry> documents) {
            out.println("Got document list of bucket " + bucketId.toString());
            for (DocumentListEntry entry : documents) {
                entry.getDocument().setLastModified(entry.getTimestamp());
                onDocument(entry.getDocument(), entry.getTimestamp());
            }
        }

        private void onEmptyBuckets(List<BucketId> bucketIds) {
            StringBuilder buckets = new StringBuilder();
            for(BucketId bid : bucketIds) {
                buckets.append(" ");
                buckets.append(bid.toString());
            }
            log.log(Level.INFO, "Got EmptyBuckets: " + buckets);
        }

        @Override
        public synchronized void onDone() {
            if (jsonOutput && !printIds) {
                if (first) {
                    out.print('[');
                }
                out.println("]");
            }
            statisticsMap.dumpAll();
            super.onDone();
        }
    }
}
