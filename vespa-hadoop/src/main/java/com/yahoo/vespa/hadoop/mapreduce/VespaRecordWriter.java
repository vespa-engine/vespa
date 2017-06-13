// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hadoop.mapreduce;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import java.util.Random;
import java.util.StringTokenizer;
import java.util.logging.Logger;

import javax.xml.namespace.QName;
import javax.xml.stream.FactoryConfigurationError;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;

import org.apache.hadoop.mapreduce.RecordWriter;
import org.apache.hadoop.mapreduce.TaskAttemptContext;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.yahoo.vespa.hadoop.mapreduce.util.VespaConfiguration;
import com.yahoo.vespa.hadoop.mapreduce.util.VespaCounters;
import com.yahoo.vespa.hadoop.pig.VespaDocumentOperation;
import com.yahoo.vespa.http.client.FeedClient;
import com.yahoo.vespa.http.client.FeedClientFactory;
import com.yahoo.vespa.http.client.Result;
import com.yahoo.vespa.http.client.config.Cluster;
import com.yahoo.vespa.http.client.config.ConnectionParams;
import com.yahoo.vespa.http.client.config.Endpoint;
import com.yahoo.vespa.http.client.config.FeedParams;
import com.yahoo.vespa.http.client.config.FeedParams.DataFormat;
import com.yahoo.vespa.http.client.config.SessionParams;
import org.apache.hadoop.mapreduce.v2.app.job.Task;

/**
 * VespaRecordWriter sends the output &lt;key, value&gt; to one or more Vespa
 * endpoints.
 *
 * @author lesters
 */
@SuppressWarnings("rawtypes")
public class VespaRecordWriter extends RecordWriter {

    private final static Logger log = Logger.getLogger(VespaRecordWriter.class.getCanonicalName());

    private boolean initialized = false;
    private FeedClient feedClient;

    private final VespaCounters counters;
    private final VespaConfiguration configuration;
    private final int progressInterval;

    VespaRecordWriter(VespaConfiguration configuration, VespaCounters counters) {
        this.counters = counters;
        this.configuration = configuration;
        this.progressInterval = configuration.progressInterval();
    }


    @Override
    public void write(Object key, Object data) throws IOException, InterruptedException {
        if (!initialized) {
            initialize();
        }

        String doc = data.toString().trim();

        // Parse data to find document id - if none found, skip this write
        String docId = DataFormat.JSON_UTF8.equals(configuration.dataFormat()) ? findDocId(doc)
                : findDocIdFromXml(doc);
        if (docId != null && docId.length() >= 0) {
            feedClient.stream(docId, doc);
            counters.incrementDocumentsSent(1);
        } else {
            counters.incrementDocumentsSkipped(1);
        }

        if (counters.getDocumentsSent() % progressInterval == 0) {
            String progress = String.format("Feed progress: %d / %d / %d / %d (sent, ok, failed, skipped)",
                    counters.getDocumentsSent(),
                    counters.getDocumentsOk(),
                    counters.getDocumentsFailed(),
                    counters.getDocumentsSkipped());
            log.info(progress);
        }

    }


    @Override
    public void close(TaskAttemptContext taskAttemptContext) throws IOException, InterruptedException {
        if (feedClient != null) {
            feedClient.close();
        }
    }


    private void initialize() {
        if (!configuration.dryrun() && configuration.randomSartupSleepMs() > 0) {
            int delay = new Random().nextInt(configuration.randomSartupSleepMs());
            log.info("VespaStorage: Delaying startup by " + delay + " ms");
            try {
                Thread.sleep(delay);
            } catch (Exception e) {}
        }

        ConnectionParams.Builder connParamsBuilder = new ConnectionParams.Builder();
        connParamsBuilder.setDryRun(configuration.dryrun());
        connParamsBuilder.setUseCompression(configuration.useCompression());
        connParamsBuilder.setEnableV3Protocol(configuration.useV3Protocol());
        connParamsBuilder.setNumPersistentConnectionsPerEndpoint(configuration.numConnections());
        connParamsBuilder.setMaxRetries(configuration.numRetries());
        if (configuration.proxyHost() != null) {
            connParamsBuilder.setProxyHost(configuration.proxyHost());
        }
        if (configuration.proxyPort() >= 0) {
            connParamsBuilder.setProxyPort(configuration.proxyPort());
        }

        FeedParams.Builder feedParamsBuilder = new FeedParams.Builder();
        feedParamsBuilder.setDataFormat(configuration.dataFormat());
        feedParamsBuilder.setRoute(configuration.route());
        feedParamsBuilder.setMaxSleepTimeMs(configuration.maxSleepTimeMs());
        feedParamsBuilder.setMaxInFlightRequests(configuration.maxInFlightRequests());
        feedParamsBuilder.setLocalQueueTimeOut(3600*1000); //1 hour queue timeout

        SessionParams.Builder sessionParams = new SessionParams.Builder();
        sessionParams.setThrottlerMinSize(configuration.throttlerMinSize());
        sessionParams.setConnectionParams(connParamsBuilder.build());
        sessionParams.setFeedParams(feedParamsBuilder.build());
        sessionParams.setClientQueueSize(configuration.maxInFlightRequests()*2);


        String endpoints = configuration.endpoint();
        StringTokenizer tokenizer = new StringTokenizer(endpoints, ",");
        while (tokenizer.hasMoreTokens()) {
            String endpoint = tokenizer.nextToken().trim();
            sessionParams.addCluster(new Cluster.Builder().addEndpoint(
                    Endpoint.create(endpoint, 4080, false)
            ).build());
        }

        ResultCallback resultCallback = new ResultCallback(counters);
        feedClient = FeedClientFactory.create(sessionParams.build(), resultCallback);

        initialized = true;
        log.info("VespaStorage configuration:\n" + configuration.toString());
        log.info(feedClient.getStatsAsJson());
    }

    private String findDocIdFromXml(String xml) {
        try {
            XMLEventReader eventReader = XMLInputFactory.newInstance().createXMLEventReader(new StringReader(xml));
            while (eventReader.hasNext()) {
                XMLEvent event = eventReader.nextEvent();
                if (event.getEventType() == XMLEvent.START_ELEMENT) {
                    StartElement element = event.asStartElement();
                    String elementName = element.getName().getLocalPart();
                    if (VespaDocumentOperation.Operation.valid(elementName)) {
                        return element.getAttributeByName(QName.valueOf("documentid")).getValue();
                    }
                }
            }
        } catch (XMLStreamException | FactoryConfigurationError e) {
            // as json dude does
            return null;
        }
        return null;
    }
    
    private String findDocId(String json) throws IOException {
        JsonFactory factory = new JsonFactory();
        try(JsonParser parser = factory.createParser(json)) {
            if (parser.nextToken() != JsonToken.START_OBJECT) {
                return null;
            }
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                String fieldName = parser.getCurrentName();
                parser.nextToken();
                if (VespaDocumentOperation.Operation.valid(fieldName)) {
                    String docId = parser.getText();
                    return docId;
                } else {
                    parser.skipChildren();
                }
            }
        } catch (JsonParseException ex) {
            return null;
        }
        return null;
    }


    static class ResultCallback implements FeedClient.ResultCallback {
        final VespaCounters counters;

        public ResultCallback(VespaCounters counters) {
            this.counters = counters;
        }

        @Override
        public void onCompletion(String docId, Result documentResult) {
            if (!documentResult.isSuccess()) {
                counters.incrementDocumentsFailed(1);
                StringBuilder sb = new StringBuilder();
                sb.append("Problems with docid ");
                sb.append(docId);
                sb.append(": ");
                List<Result.Detail> details = documentResult.getDetails();
                for (Result.Detail detail : details) {
                    sb.append(detail.toString());
                    sb.append(" ");
                }
                log.warning(sb.toString());
                return;
            }
            counters.incrementDocumentsOk(1);
        }

    }

}
