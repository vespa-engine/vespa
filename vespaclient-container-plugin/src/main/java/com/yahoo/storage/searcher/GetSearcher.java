// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.storage.searcher;

import com.google.inject.Inject;
import com.yahoo.cloud.config.ClusterListConfig;
import com.yahoo.cloud.config.SlobroksConfig;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.document.config.DocumentmanagerConfig;
import com.yahoo.feedhandler.NullFeedMetric;
import com.yahoo.processing.request.CompoundName;
import com.yahoo.vespa.config.content.LoadTypeConfig;
import com.yahoo.document.DataType;
import com.yahoo.document.Document;
import com.yahoo.document.DocumentId;
import com.yahoo.document.Field;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.documentapi.messagebus.protocol.GetDocumentMessage;
import com.yahoo.documentapi.messagebus.protocol.GetDocumentReply;
import com.yahoo.feedapi.*;
import com.yahoo.log.LogLevel;
import com.yahoo.messagebus.Reply;
import com.yahoo.search.Query;
import com.yahoo.search.query.Properties;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.result.DefaultErrorHit;
import com.yahoo.search.result.ErrorMessage;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.vespaclient.config.FeederConfig;
import org.brotli.dec.BrotliInputStream;

import java.io.*;
import java.util.*;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;

/**
 * Searcher component to make GET requests to a content cluster.
 * <p>
 * Document ID must be given either as 1 "id=docid" query parameter
 * for single-document GETs and 1-n "id[0]=docid_1&amp;id[1]=...&amp;id[n-1]=docid_n"
 * parameters for multi-document GETs.
 *
 * <p>
 * Standard gateway query parameters are implicitly supported:
 *   priority, timeout, route
 *
 * <p>
 * The searcher also accepts the following (optional) query parameters:
 *   headersonly=true|false (default: false)
 *   For specifying whether or not to return only header fields.
 *
 * <p>
 *   field=string (default: no parameter specified)
 *   For getting a single document field.
 *
 * <p>
 *   contenttype=string (default: no content type specified)
 *   For specifiying the returned HTTP header content type for a returned
 *   document field's content. field must also be specified.
 */
@SuppressWarnings("deprecation")
public class GetSearcher extends Searcher {

    private static final Logger log = Logger.getLogger(GetSearcher.class.getName());

    private static final CompoundName ID = new CompoundName("id");
    private static final CompoundName HEADERS_ONLY = new CompoundName("headersonly");
    private static final CompoundName POPULATE_HIT_FIELDS = new CompoundName("populatehitfields");
    private static final CompoundName FIELDSET = new CompoundName("fieldset");
    private static final CompoundName FIELD = new CompoundName("field");
    private static final CompoundName CONTENT_TYPE = new CompoundName("contenttype");
    private static final CompoundName TIEMOUT = new CompoundName("timeout");

    FeedContext context;

    private final long defaultTimeoutMillis;

    private class GetResponse implements SharedSender.ResultCallback {

        /**
         * We have to maintain the same ordering of results as that
         * given in the request. Do this by remembering the index of
         * each requested document ID.
         */
        private Map<String, Integer> ordering;
        private List<DocumentHit> documentHits = new ArrayList<>();
        private List<DefaultErrorHit> errorHits = new ArrayList<>();
        private List<Reply> replies = new ArrayList<>();
        private final SharedSender.Pending pendingNumber = new SharedSender.Pending();

        public GetResponse(List<String> documentIds) {
            ordering = new HashMap<>(documentIds.size());
            for (int i = 0; i < documentIds.size(); ++i) {
                ordering.put(documentIds.get(i), i);
            }
        }

        public boolean isAborted() {
            return false;
        }

        private String stackTraceFromException(Exception e) {
            StringWriter sw = new StringWriter();
            PrintWriter ps = new PrintWriter(sw);
            e.printStackTrace(ps);
            ps.flush();
            return sw.toString();
        }

        public boolean handleReply(Reply reply) {
            if ((reply.getTrace().getLevel() > 0) && log.isLoggable(LogLevel.DEBUG)) {
                String str = reply.getTrace().toString();
                log.log(LogLevel.DEBUG, str);
            }
            replies.add(reply);
            return true;
        }
        public SharedSender.Pending getPending() { return pendingNumber; }

        private void processReplies() {
            for (Reply reply : replies) {
                processReply(reply);
            }
        }
        private void processReply(Reply reply) {
            if (!reply.hasErrors()) {
                try {
                    addDocumentHit(reply);
                } catch (Exception e) {
                    String msg = "Got exception of type " + e.getClass().getName()
                                 + " during document deserialization: " + e.getMessage();
                    errorHits.add(new DefaultErrorHit("GetSearcher", ErrorMessage.createInternalServerError(msg)));
                    log.log(LogLevel.DEBUG, "Got exception during document deserialization: " + stackTraceFromException(e));
                }
            } else {
                errorHits.add(new DefaultErrorHit("GetSearcher", new MessageBusErrorMessage(
                              reply.getError(0).getCode(), 0, reply.getError(0).getMessage())));
                if (log.isLoggable(LogLevel.DEBUG)) {
                    log.log(LogLevel.DEBUG, "Received error reply with message " + reply.getError(0).getMessage());
                }
            }
        }

        private void addDocumentHit(Reply reply) {
            Document doc = ((GetDocumentReply)reply).getDocument();
            GetDocumentMessage msg = (GetDocumentMessage)reply.getMessage();
            Integer index = ordering.get(msg.getDocumentId().toString());
            if (index == null) { // Shouldn't happen
                throw new IllegalStateException("Received GetDocumentReply for unknown document: "
                                                + doc.getId().toString());
            }
            if (doc != null) {
                documentHits.add(new DocumentHit(doc, index));
                if (log.isLoggable(LogLevel.DEBUG)) {
                    log.log(LogLevel.DEBUG, "Received GetDocumentReply for "
                                            + doc.getId().toString());
                }
            } else {
                // Don't add a hit for documents that can't be found
                if (log.isLoggable(LogLevel.DEBUG)) {
                    log.log(LogLevel.DEBUG, "Received empty (not found) GetDocumentReply for "
                                            + msg.getDocumentId().toString());
                }
            }
        }

        private class IndexComparator implements Comparator<DocumentHit> {
            public int compare(DocumentHit o1, DocumentHit o2) {
                return o1.getIndex() - o2.getIndex();
            }
        }

        public void addHitsToResult(Result result, boolean populateHitFields) {
            for (DefaultErrorHit hit : errorHits) {
                result.hits().add(hit);
            }
            // Sort document hits according to their request index
            Collections.sort(documentHits, new IndexComparator());
            for (DocumentHit hit : documentHits) {
                if (populateHitFields) {
                    hit.populateHitFields();
                }
                result.hits().add(hit);
            }
            result.setTotalHitCount(documentHits.size());
        }

        public List<DocumentHit> getDocumentHits() {
            return documentHits;
        }

        public List<DefaultErrorHit> getErrorHits() {
            return errorHits;
        }
    }

    @Inject
    public GetSearcher(FeederConfig feederConfig, 
                       LoadTypeConfig loadTypeConfig,
                       DocumentmanagerConfig documentmanagerConfig,
                       SlobroksConfig slobroksConfig,
                       ClusterListConfig clusterListConfig) throws Exception {
        this(FeedContext.getInstance(feederConfig, loadTypeConfig, documentmanagerConfig, slobroksConfig, 
                                     clusterListConfig, new NullFeedMetric()), (long)(feederConfig.timeout() * 1000));
    }

    GetSearcher(FeedContext context) throws Exception {
        this.context = context;
        this.defaultTimeoutMillis = context.getPropertyProcessor().getDefaultTimeoutMillis();
    }

    GetSearcher(FeedContext context, long defaultTimeoutMillis) throws Exception {
        this.context = context;
        this.defaultTimeoutMillis = defaultTimeoutMillis;
    }

    private static void postValidateDocumentIdParameters(Properties properties, int arrayIdsFound) throws Exception {
        for (Map.Entry<String, Object> kv : properties.listProperties().entrySet()) {
            if (!kv.getKey().startsWith("id[")) {
                continue;
            }
            if (!kv.getKey().endsWith("]")) {
                throw new IllegalArgumentException("Malformed document ID array parameter");
            }
            String indexStr = kv.getKey().substring(3, kv.getKey().length() - 1);
            int idx = Integer.parseInt(indexStr);
            if (idx >= arrayIdsFound) {
                throw new IllegalArgumentException("query contains document ID array " +
                        "that is not zero-based and/or linearly increasing");
            }
        }
    }

    private List<String> getDocumentIds(Query query) throws Exception {
        Properties properties = query.properties();
        List<String> docIds = new ArrayList<>();

        // First check for regular "id=XX" syntax. If found, return vector with that
        // document id only
        String singleId = properties.getString(ID);

        int index = 0;
        if (singleId != null) {
            docIds.add(singleId);
        } else {
            // Check for id[0]=XX&id[1]=YY...id[n]=ZZ syntax. Indices always start
            // at 0 and are always increased by 1.
            while (true) {
                String docId = properties.getString("id[" + index + "]");
                if (docId == null) {
                    break;
                }
                docIds.add(docId);
                ++index;
            }
            postValidateDocumentIdParameters(properties, index);
        }

        handleData(query.getHttpRequest(), docIds);
        return docIds;
    }

    private void handleData(HttpRequest request, List<String> docIds) throws IOException {
        if (request.getData() != null) {
            InputStream input;
            if ("br".equals(request.getHeader("Content-Encoding"))) {
                input = new BrotliInputStream(request.getData());
            }
            else if ("gzip".equals(request.getHeader("Content-Encoding"))) {
                input = new GZIPInputStream(request.getData());
            } else {
                input = request.getData();
            }
            InputStreamReader reader = new InputStreamReader(input, "UTF-8");
            BufferedReader lineReader = new BufferedReader(reader);
            String line;
            while ((line = lineReader.readLine()) != null) {
                docIds.add(line);
            }
        }
    }

    private void handleFieldFiltering(GetResponse response, Result result,
                                      String fieldName, String contentType,
                                      boolean headersOnly) {

        if (response.getDocumentHits().isEmpty()) {
            result.hits().addError(ErrorMessage.createNotFound(
                    "Document not found, could not return field '" + fieldName + "'"));
            return;
        }

        if (result.hits().getErrorHit() == null) {
            Document doc = response.getDocumentHits().get(0).getDocument();
            Field field = doc.getDataType().getField(fieldName);
            boolean wrapXml = false;

            if (field == null) {
                result.hits().addError(ErrorMessage.createIllegalQuery(
                        "Field '" + fieldName + "' not found in document type"));
                return;
            }
            FieldValue value = doc.getFieldValue(field);
            // If the field exists but hasn't been set in this document, the
            // content will be null. We treat this as an error.
            if (value == null) {
                if (!field.isHeader() && headersOnly) {
                    // TODO(vekterli): make this work with field sets as well.
                    result.hits().addError(ErrorMessage.createInvalidQueryParameter(
                            "Field '" + fieldName + "' is located in document body, but headersonly "
                            + "prevents it from being retrieved in " + doc.getId().toString()));
                } else {
                    result.hits().addError(ErrorMessage.createNotFound(
                            "Field '" + fieldName + "' found in document type, but had "
                            + "no content in " + doc.getId().toString()));
                }
                return;
            }
            String encoding = null;
            if (field.getDataType() == DataType.RAW) {
                if (contentType == null) {
                    contentType = "application/octet-stream";
                }
                encoding = "ISO-8859-1";
            } else {
                // By default, return field wrapped in a blanket of vespa XML
                contentType = "text/xml";
                wrapXml = true;
            }
            if (encoding == null) {
                // Encoding doesn't matter for binary content, since we're always
                // writing directly to the byte buffer and not through a charset
                // encoder. Presumably, the client is intelligent enough to not
                // attempt to UTF-8 decode binary data.
                encoding = "UTF-8";
            }
            // Add hit now that we know there aren't any field errors. Otherwise,
            // there would be both an error hit and a document hit in the result
            response.addHitsToResult(result, false);
            // Override Vespa XML template
            result.getTemplating().setTemplates(new DocumentFieldTemplate(field, contentType, encoding, wrapXml));
        }
        // else: return with error hit, invoking regular Vespa XML error template
    }

    private void validateParameters(String fieldName, String contentType,
                                            List<String> documentIds) {
        // Content-type only makes sense for single document queries with a field
        // set
        if (contentType != null) {
            if (documentIds.size() > 1) {
                throw new IllegalArgumentException(
                        "contenttype parameter only valid for single document id query");
            }
            if (fieldName == null) {
                throw new IllegalArgumentException(
                        "contenttype set without document field being specified");
            }
        }
        if (fieldName != null && documentIds.size() > 1) {
            throw new IllegalArgumentException(
                        "Field only valid for single document id query");
        }
    }

    // For unit testing
    protected MessagePropertyProcessor getMessagePropertyProcessor() {
        return context.getPropertyProcessor();
    }

    private void doGetDocuments(Query query, Result result, List<String> documentIds) {
        GetResponse response = new GetResponse(documentIds);
        Properties properties = query.properties();

        boolean headersOnly = properties.getBoolean(HEADERS_ONLY, false);
        boolean populateHitFields = properties.getBoolean(POPULATE_HIT_FIELDS, false);
        String fieldSet     = properties.getString(FIELDSET);
        String fieldName    = properties.getString(FIELD);
        String contentType  = properties.getString(CONTENT_TYPE);
        long timeoutMillis = properties.getString(TIEMOUT) != null ? query.getTimeout() : defaultTimeoutMillis;

        if (fieldSet == null) {
            fieldSet = headersOnly ? "[header]" : "[all]";
        }

        validateParameters(fieldName, contentType, documentIds);

        MessagePropertyProcessor.PropertySetter propertySetter;
        propertySetter = context.getPropertyProcessor().buildPropertySetter(query.getHttpRequest());

        SingleSender sender = new SingleSender(response, context.getSharedSender(propertySetter.getRoute().toString()));
        sender.addMessageProcessor(propertySetter);

        sendDocumentGetMessages(documentIds, fieldSet, sender);
        // Twiddle thumbs until we've received a reply for all documents
        sender.done();
        boolean completed = sender.waitForPending(timeoutMillis);
        if ( ! completed) {
            result.hits().addError(ErrorMessage.createTimeout(
                    "Timed out after waiting "+timeoutMillis+" ms for responses"));
        }
        response.processReplies();
        if (fieldName != null) {
            handleFieldFiltering(response, result, fieldName, contentType, headersOnly);
        } else {
            response.addHitsToResult(result, populateHitFields);
        }
    }

    private void sendDocumentGetMessages(List<String> documentIds, String fieldSet, SingleSender sender) {
        for (String docIdStr : documentIds) {
            DocumentId docId = new DocumentId(docIdStr);
            GetDocumentMessage getMsg = new GetDocumentMessage(docId, fieldSet);

            sender.send(getMsg);
            if (log.isLoggable(LogLevel.DEBUG)) {
                log.log(LogLevel.DEBUG, "Sent GetDocumentMessage for "
                        + docId.toString());
            }
        }
    }

    boolean verifyBackendDocumentHitsOnly(Result result) {
        if (result.hits().size() != 0) {
            log.log(LogLevel.DEBUG, "Result had hits after being sent down");
            for (int i = 0; i < result.hits().size(); ++i) {
                if (!(result.hits().get(i) instanceof DocumentHit)) {
                    log.log(LogLevel.WARNING, "Got hit from backend searcher which was "
                            + "not a com.yahoo.storage.searcher.DocumentHit instance: "
                            + result.hits().get(i).getClass().getName());
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public Result search(Query query, Execution execution) {
        // Pass through to next searcher
        Result result = execution.search(query);

        List<String> documentIds;
        try {
            documentIds = getDocumentIds(query);
        } catch (Exception e) {
            setOutputFormat(query, result);
            result.hits().addError(ErrorMessage.createIllegalQuery(e.getClass().getName() + ": " + e.getMessage()));
            return result;
        }
        // Early-out for pass-through queries
        if (documentIds.isEmpty()) {
            return result;
        }
        // Make sure we don't try to combine non-document hits and document hits
        // in the same result set.
        if (!verifyBackendDocumentHitsOnly(result)) {
            result = new Result(query); // Don't include unknown hits
            setOutputFormat(query, result);
            result.hits().addError(ErrorMessage.createInternalServerError(
                    "A backend searcher to com.yahoo.storage.searcher.GetSearcher " +
                    "returned a hit that was not an instance of com.yahoo.storage.searcher.DocumentHit. " +
                    "Only DocumentHit instances are supported in the backend hit result set when doing " +
                    "queries that contain document identifier sets recognised by the Get Searcher."));
            return result;
        }
        setOutputFormat(query, result);
        // Do not propagate exceptions back up, as we want to have all errors
        // be reported using the proper template
        try {
            doGetDocuments(query, result, documentIds);
            query.setHits(result.hits().size());
        } catch (IllegalArgumentException e) {
            result.hits().addError(ErrorMessage.createIllegalQuery(e.getClass().getName() + ": " + e.getMessage()));
        } catch (Exception e) {
            result.hits().addError(ErrorMessage.createUnspecifiedError(e.getClass().getName() + ": " + e.getMessage()));
        }

        return result;
    }

    private static final CompoundName formatShortcut = new CompoundName("format");
    private static final CompoundName format = new CompoundName("presentation.format");

    /**
     * Use custom XML output format unless the default JSON renderer is specified in the request.
     */
    @SuppressWarnings("deprecation")
    static void setOutputFormat(Query query, Result result) {
        if (getRequestProperty(formatShortcut, "", query).equals("JsonRenderer")) return;
        if (getRequestProperty(format, "", query).equals("JsonRenderer")) return;
        if (getRequestProperty(formatShortcut, "", query).equals("json")) return;
        if (getRequestProperty(format, "", query).equals("json")) return;
        result.getTemplating().setTemplates(new DocumentXMLTemplate());
    }

    private static String getRequestProperty(CompoundName propertyName, String defaultValue, Query query) {
        String propertyValue = query.getHttpRequest().getProperty(propertyName.toString());
        if (propertyValue == null) return defaultValue;
        return propertyValue;
    }

}
