// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi;

import com.yahoo.document.BucketId;
import com.yahoo.document.FixedBucketSpaces;
import com.yahoo.documentapi.messagebus.loadtypes.LoadType;
import com.yahoo.documentapi.messagebus.protocol.DocumentProtocol;
import com.yahoo.messagebus.ThrottlePolicy;
import com.yahoo.messagebus.routing.Route;
import com.yahoo.text.Utf8;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Parameters for creating or opening a visitor session
 *
 * @author Håkon Humberset
 */
public class VisitorParameters extends Parameters {

    private String documentSelection;
    private String bucketSpace = FixedBucketSpaces.defaultSpace();
    private String visitorLibrary = "DumpVisitor";
    private int maxPending = 32;
    private long timeoutMs = -1;
    private long sessionTimeoutMs = -1;
    private long fromTimestamp = 0;
    private long toTimestamp = 0;
    boolean visitRemoves = false;
    private String fieldSet = "[all]";
    boolean visitInconsistentBuckets = false;
    private ProgressToken resumeToken = null;
    private String resumeFileName = "";
    private String remoteDataHandler = null;
    private VisitorDataHandler localDataHandler;
    private VisitorControlHandler controlHandler;
    private Map<String, byte []> libraryParameters = new TreeMap<String, byte []>();
    private Route visitRoute = null;
    private float weight = 1;
    private long maxFirstPassHits = -1;
    private long maxTotalHits = -1;
    private int visitorOrdering = 0;
    private int maxBucketsPerVisitor = 1;
    private boolean dynamicallyIncreaseMaxBucketsPerVisitor = false;
    private float dynamicMaxBucketsIncreaseFactor = 2;
    private LoadType loadType = LoadType.DEFAULT;
    private DocumentProtocol.Priority priority = null;
    private int traceLevel = 0;
    private ThrottlePolicy throttlePolicy = null;
    private boolean skipBucketsOnFatalErrors = false;

    // Advanced parameter, only for internal use.
    Set<BucketId> bucketsToVisit = null;

    /**
     * Creates visitor parameters from a document selection expression, using
     * defaults for other parameters.
     *
     * @param documentSelection document selection expression
     */
    public VisitorParameters(String documentSelection) {
        this.documentSelection = documentSelection;
    }

    /**
     * Copy constructor.
     *
     * @param params object to copy
     */
    public VisitorParameters(VisitorParameters params) {
        setDocumentSelection(params.getDocumentSelection());
        setBucketSpace(params.getBucketSpace());
        setVisitorLibrary(params.getVisitorLibrary());
        setMaxPending(params.getMaxPending());
        setTimeoutMs(params.getTimeoutMs());
        setFromTimestamp(params.getFromTimestamp());
        setToTimestamp(params.getToTimestamp());
        visitRemoves(params.visitRemoves());
        fieldSet(params.fieldSet());
        visitInconsistentBuckets(params.visitInconsistentBuckets());
        setLibraryParameters(params.getLibraryParameters());
        setRoute(params.getRoute());
        setResumeFileName(params.getResumeFileName());
        setResumeToken(params.getResumeToken());
        if (params.getRemoteDataHandler() != null) {
            setRemoteDataHandler(params.getRemoteDataHandler());
        } else {
            setLocalDataHandler(params.getLocalDataHandler());
        }
        setControlHandler(params.getControlHandler());
        setMaxFirstPassHits(params.getMaxFirstPassHits());
        setMaxTotalHits(params.getMaxTotalHits());
        setVisitorOrdering(params.getVisitorOrdering());
        setMaxBucketsPerVisitor(params.getMaxBucketsPerVisitor());
        setLoadType(params.getLoadType());
        setPriority(params.getPriority());
        setDynamicallyIncreaseMaxBucketsPerVisitor(
                params.getDynamicallyIncreaseMaxBucketsPerVisitor());
        setDynamicMaxBucketsIncreaseFactor(
                params.getDynamicMaxBucketsIncreaseFactor());
        setTraceLevel(params.getTraceLevel());
        skipBucketsOnFatalErrors(params.skipBucketsOnFatalErrors());
    }

    // Get functions

    // TODO: s/@return/Returns/ - this javadoc will not contain text in the method overview

    /** @return The selection string used for visiting. */
    public String getDocumentSelection() { return documentSelection; }

    /** @return The bucket space to visit */
    public String getBucketSpace() { return bucketSpace; }

    /** @return What visitor library to use for the visiting. The library in question must be installed on each storage node in the target cluster. */
    public String getVisitorLibrary() { return visitorLibrary; }

    /** @return The maximum number of messages each storage visitor will have pending before waiting for acks from client. */
    public int getMaxPending() { return maxPending; }

    /** @return The timeout for each sent visitor operation in milliseconds. */
    public long getTimeoutMs() { return timeoutMs; }

    /**
     * @return Session timeout in milliseconds, or -1 if not timeout has been set. -1 implies
     *         that session will run to completion without automatically timing out.
     */
    public long getSessionTimeoutMs() { return sessionTimeoutMs; }

    /** @return The minimum timestamp (in microsecs) of documents the visitor will visit. */
    public long getFromTimestamp() { return fromTimestamp; }

    /** @return The maximum timestamp (in microsecs) of documents the visitor will visit. */
    public long getToTimestamp() { return toTimestamp; }

    /** @return If this method returns true, the visitor will visit remove entries as well as documents (you can see what documents have been deleted). */
    public boolean visitRemoves() { return visitRemoves; }

    public boolean getVisitRemoves() { return visitRemoves; }

    public boolean getVisitHeadersOnly() { return "[header]".equals(fieldSet); }

    /** @return The field set to use. */
    public String fieldSet() { return fieldSet; }

    public String getFieldSet() { return fieldSet; }

    /** @return If this method returns true, the visitor will visit inconsistent buckets. */
    public boolean visitInconsistentBuckets() { return visitInconsistentBuckets; }

    public boolean getVisitInconsistentBuckets() { return visitInconsistentBuckets; }

    /** @return Returns a map of string → string of arguments that are passed to the visitor library. */
    public Map<String, byte []> getLibraryParameters() { return libraryParameters; }

    /** @return The progress token, which can be used to resume visitor. */
    public ProgressToken getResumeToken() { return resumeToken; }

    /** @return The filename for reading/storing progress token. */
    public String getResumeFileName() { return resumeFileName; }

    /** @return Address to the remote data handler. */
    public String getRemoteDataHandler() { return remoteDataHandler; }

    /** @return The local data handler. */
    public VisitorDataHandler getLocalDataHandler() { return localDataHandler; }

    /** @return The control handler. */
    public VisitorControlHandler getControlHandler() { return controlHandler; }

    /** @return Whether or not max buckets per visitor value should be dynamically
     *     increased when using orderdoc and visitors do not return at least half
     *     the desired amount of documents
     */
    public boolean getDynamicallyIncreaseMaxBucketsPerVisitor() {
        return dynamicallyIncreaseMaxBucketsPerVisitor;
    }

    /** @return Factor with which max buckets are dynamically increased each time */
    public float getDynamicMaxBucketsIncreaseFactor() {
        return dynamicMaxBucketsIncreaseFactor;
    }

    public DocumentProtocol.Priority getPriority() {
        if (priority != null) {
            return priority;
        } else if (loadType != null) {
            return loadType.getPriority();
        } else {
            return DocumentProtocol.Priority.NORMAL_3;
        }
    }

    // Set functions

    /** Set the document selection expression */
    public void setDocumentSelection(String selection) { documentSelection = selection; }

    /** Set which (single) bucket space this visiting will be against. */
    public void setBucketSpace(String bucketSpace) { this.bucketSpace = bucketSpace; }

    /** Set which visitor library is used for visiting in storage. DumpVisitor is most common implementation. */
    public void setVisitorLibrary(String library) { visitorLibrary = library; }

    /** Set maximum pending messages one storage visitor will have pending to this client before stalling, waiting for acks. */
    public void setMaxPending(int maxPending) { this.maxPending = maxPending; }

    /** Set the timeout for each visitor command in milliseconds. */
    public void setTimeoutMs(long timeoutMs) { this.timeoutMs = timeoutMs; }

    /**
     * Sets timeout for the entire visiting session, in milliseconds. -1 implies infinity.
     *
     * If the session takes more time than this to complete, it will automatically
     * be failed with CompletionCode.TIMEOUT.
     * If no session timeout has been explicitly set (or it has been set to -1), visiting will
     * continue until it completes or abort()/destroy() is called on the session instance.
     */
    public void setSessionTimeoutMs(long timeoutMs) { this.sessionTimeoutMs = timeoutMs; }

    /** Set from timestamp in microseconds. Documents put/updated before this timestamp will not be visited. */
    public void setFromTimestamp(long timestamp) { fromTimestamp = timestamp; }

    /** Set to timestamp in microseconds. Documents put/updated after this timestamp will not be visited. */
    public void setToTimestamp(long timestamp) { toTimestamp = timestamp; }

    /** Set whether to visit remove entries. That is, entries saying that some document has been removed. */
    public void visitRemoves(boolean visitRemoves) { this.visitRemoves = visitRemoves; }

    public void setVisitRemoves(boolean visitRemoves) { this.visitRemoves = visitRemoves; }

    public void setVisitHeadersOnly(boolean headersOnly) { this.fieldSet = headersOnly ? "[header]" : "[all]"; }

    /** Set field set to use. */
    public void fieldSet(String fieldSet) { this.fieldSet = fieldSet; }
    public void setFieldSet(String fieldSet) { this.fieldSet = fieldSet; }

    /** Set whether to visit inconsistent buckets. */
    public void visitInconsistentBuckets(boolean visitInconsistentBuckets) { this.visitInconsistentBuckets = visitInconsistentBuckets; }

    public void setVisitInconsistentBuckets(boolean visitInconsistentBuckets) { this.visitInconsistentBuckets = visitInconsistentBuckets; }

    /** Set a visitor library specific parameter. */
    public void setLibraryParameter(String param, String value) {
        libraryParameters.put(param, Utf8.toBytes(value));
    }

    /** Set a visitor library specific parameter. */
    public void setLibraryParameter(String param, byte [] value) { libraryParameters.put(param, value); }

    /** Set all visitor library specific parameters. */
    public void setLibraryParameters(Map<String, byte []> params) { libraryParameters = params; }

    /** Set progress token, which can be used to resume visitor. */
    public void setResumeToken(ProgressToken token) { resumeToken = token; }

    /**
     * Set filename for reading/storing progress token. If the file exists and
     * contains progress data, visitor should resume visiting from this point.
     */
    public void setResumeFileName(String fileName) { resumeFileName = fileName; }

    /** Set address for the remote data handler. */
    public void setRemoteDataHandler(String remoteDataHandler) { this.remoteDataHandler = remoteDataHandler; localDataHandler = null; }

    /** Set local data handler. */
    public void setLocalDataHandler(VisitorDataHandler localDataHandler) { this.localDataHandler = localDataHandler; remoteDataHandler = null; }

    /** Set control handler. */
    public void setControlHandler(VisitorControlHandler controlHandler) { this.controlHandler = controlHandler; }

    /** Set the name of the storage cluster route to visit. Default is "storage/cluster.storage". */
    public void setRoute(String route) { setRoute(Route.parse(route)); }

    /** Set the route to visit. */
    public void setRoute(Route route) { visitRoute = route; }

    /** @return Returns the name of the storage cluster to visit. */
    // TODO: Document: Where is the default - does this ever return null, or does it return "storage" if input is null?
    public Route getRoute() { return visitRoute; }

    /** Set the maximum number of documents to visit (max documents returned by the visitor) */
    public void setMaxFirstPassHits(long max) { maxFirstPassHits = max; }

    /** @return Returns the maximum number of documents to visit (max documents returned by the visitor) */
    public long getMaxFirstPassHits() { return maxFirstPassHits; }

    /** Set the maximum number of documents to visit (max documents returned by the visitor) */
    public void setMaxTotalHits(long max) { maxTotalHits = max; }

    /** @return Returns the maximum number of documents to visit (max documents returned by the visitor) */
    public long getMaxTotalHits() { return maxTotalHits; }

    public Set<BucketId> getBucketsToVisit() { return bucketsToVisit; }

    public void setBucketsToVisit(Set<BucketId> buckets) { bucketsToVisit = buckets; }

    public int getVisitorOrdering() { return visitorOrdering; }

    public void setVisitorOrdering(int order) { visitorOrdering = order; }

    public int getMaxBucketsPerVisitor() { return maxBucketsPerVisitor; }

    public void setMaxBucketsPerVisitor(int max) { maxBucketsPerVisitor = max; }

    public void setTraceLevel(int traceLevel) { this.traceLevel = traceLevel; }

    public int getTraceLevel() { return traceLevel; }

    public void setPriority(DocumentProtocol.Priority priority) {
        this.priority = priority;
    }

    public ThrottlePolicy getThrottlePolicy() {
        return throttlePolicy;
    }

    public void setThrottlePolicy(ThrottlePolicy policy) {
        throttlePolicy = policy;
    }

    public void setLoadType(LoadType loadType) {
        this.loadType = loadType;
    }

    public LoadType getLoadType() {
        return loadType;
    }

    public boolean skipBucketsOnFatalErrors() { return skipBucketsOnFatalErrors; }

    public void skipBucketsOnFatalErrors(boolean skipBucketsOnFatalErrors) { this.skipBucketsOnFatalErrors = skipBucketsOnFatalErrors; }

    /**
     * Set whether or not max buckets per visitor value should be dynamically
     * increased when using orderdoc and visitors do not return at least half
     * the desired amount of documents
     *
     * @param dynamicallyIncreaseMaxBucketsPerVisitor whether or not to increase
     */
    public void setDynamicallyIncreaseMaxBucketsPerVisitor(boolean dynamicallyIncreaseMaxBucketsPerVisitor) {
        this.dynamicallyIncreaseMaxBucketsPerVisitor = dynamicallyIncreaseMaxBucketsPerVisitor;
    }

    /**
     * Set factor with which max buckets are dynamically increased each time
     * @param dynamicMaxBucketsIncreaseFactor increase factor (must be 1 or more)
     */
    public void setDynamicMaxBucketsIncreaseFactor(float dynamicMaxBucketsIncreaseFactor) {
        this.dynamicMaxBucketsIncreaseFactor = dynamicMaxBucketsIncreaseFactor;
    }

    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append("VisitorParameters(\n")
                .append("  Document selection: ").append(documentSelection).append('\n')
                .append("  Bucket space:       ").append(bucketSpace).append('\n')
                .append("  Visitor library:    ").append(visitorLibrary).append('\n')
                .append("  Max pending:        ").append(maxPending).append('\n')
                .append("  Timeout (ms):       ").append(timeoutMs).append('\n')
                .append("  Time period:        ").append(fromTimestamp).append(" - ").append(toTimestamp).append('\n');
        if (visitRemoves) {
            sb.append("  Visiting remove entries\n");
        }
        if (visitInconsistentBuckets) {
            sb.append("  Visiting inconsistent buckets\n");
        }
        if (libraryParameters.size() > 0) {
            sb.append("  Visitor library parameters:\n");
            for (Map.Entry<String, byte[]> e : libraryParameters.entrySet()) {
                sb.append("    ").append(e.getKey()).append(" : ");
                sb.append(Utf8.toString(e.getValue())).append('\n');
            }
        }
        sb.append("  Field set:          ").append(fieldSet).append('\n');
        sb.append("  Route:              ").append(visitRoute).append('\n');
        sb.append("  Weight:             ").append(weight).append('\n');
        sb.append("  Max firstpass hits: ").append(maxFirstPassHits).append('\n');
        sb.append("  Max total hits:     ").append(maxTotalHits).append('\n');
        sb.append("  Visitor ordering:   ").append(visitorOrdering).append('\n');
        sb.append("  Max buckets:        ").append(maxBucketsPerVisitor).append('\n');
        sb.append("  Priority:           ").append(getPriority().toString()).append('\n');
        if (dynamicallyIncreaseMaxBucketsPerVisitor) {
            sb.append("  Dynamically increasing max buckets per visitor\n");
            sb.append("  Increase factor:    ")
                    .append(dynamicMaxBucketsIncreaseFactor)
                    .append('\n');
        }
        sb.append(')');

        return sb.toString();
    }
}
