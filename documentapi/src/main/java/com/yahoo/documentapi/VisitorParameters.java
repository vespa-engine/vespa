// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi;

import com.yahoo.document.BucketId;
import com.yahoo.document.FixedBucketSpaces;
import com.yahoo.document.fieldset.DocumentOnly;
import com.yahoo.documentapi.messagebus.protocol.DocumentProtocol;
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
    private String fieldSet = DocumentOnly.NAME;
    boolean visitInconsistentBuckets = false;
    private ProgressToken resumeToken = null;
    private String resumeFileName = "";
    private String remoteDataHandler = null;
    private VisitorDataHandler localDataHandler;
    private VisitorControlHandler controlHandler;
    private Map<String, byte []> libraryParameters = new TreeMap<>();
    private Route visitRoute = null;
    private final float weight = 1;
    private long maxTotalHits = -1;
    private int maxBucketsPerVisitor = 1;
    private DocumentProtocol.Priority priority = null;
    private int traceLevel = 0;
    private boolean skipBucketsOnFatalErrors = false;
    private int slices = 1;
    private int sliceId = 0;

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
        setMaxTotalHits(params.getMaxTotalHits());
        setMaxBucketsPerVisitor(params.getMaxBucketsPerVisitor());
        setPriority(params.getPriority());
        setTraceLevel(params.getTraceLevel());
        skipBucketsOnFatalErrors(params.skipBucketsOnFatalErrors());
        slice(params.getSlices(), getSliceId());
    }

    // Get functions

    /** Returns the selection string used for visiting. */
    public String getDocumentSelection() { return documentSelection; }

    /** Returns the bucket space to visit */
    public String getBucketSpace() { return bucketSpace; }

    /** Returns what visitor library to use for the visiting. The library in question must be installed on each storage node in the target cluster. */
    public String getVisitorLibrary() { return visitorLibrary; }

    /** Returns the maximum number of messages each storage visitor will have pending before waiting for acks from client. */
    public int getMaxPending() { return maxPending; }

    /** Returns the timeout for each sent visitor operation in milliseconds. */
    public long getTimeoutMs() { return timeoutMs; }

    /**
     * Returns session timeout in milliseconds, or -1 if not timeout has been set. -1 implies
     * that session will run to completion without automatically timing out.
     */
    public long getSessionTimeoutMs() { return sessionTimeoutMs; }

    /** Returns the minimum timestamp (in microsecs) of documents the visitor will visit. */
    public long getFromTimestamp() { return fromTimestamp; }

    /** Returns the maximum timestamp (in microsecs) of documents the visitor will visit. */
    public long getToTimestamp() { return toTimestamp; }

    /** Returns if this method returns true, the visitor will visit remove entries as well as documents (you can see what documents have been deleted). */
    public boolean visitRemoves() { return visitRemoves; }

    public boolean getVisitRemoves() { return visitRemoves; }

    /** Returns the field set to use. */
    public String fieldSet() { return fieldSet; }

    public String getFieldSet() { return fieldSet; }

    /** Returns if this method returns true, the visitor will visit inconsistent buckets. */
    public boolean visitInconsistentBuckets() { return visitInconsistentBuckets; }

    public boolean getVisitInconsistentBuckets() { return visitInconsistentBuckets; }

    /** Returns a map of string → string of arguments that are passed to the visitor library. */
    public Map<String, byte []> getLibraryParameters() { return libraryParameters; }

    /** Returns the progress token, which can be used to resume visitor. */
    public ProgressToken getResumeToken() { return resumeToken; }

    /** Returns the filename for reading/storing progress token. */
    public String getResumeFileName() { return resumeFileName; }

    /** Returns address to the remote data handler. */
    public String getRemoteDataHandler() { return remoteDataHandler; }

    /** Returns the local data handler. */
    public VisitorDataHandler getLocalDataHandler() { return localDataHandler; }

    /** Returns the control handler. */
    public VisitorControlHandler getControlHandler() { return controlHandler; }

    public DocumentProtocol.Priority getPriority() {
        if (priority != null) {
            return priority;
        } else {
            return DocumentProtocol.Priority.NORMAL_3;
        }
    }

    // Set functions

    /** Sets the document selection expression */
    public void setDocumentSelection(String selection) { documentSelection = selection; }

    /** Sets which (single) bucket space this visiting will be against. */
    public void setBucketSpace(String bucketSpace) { this.bucketSpace = bucketSpace; }

    /** Sets which visitor library is used for visiting in storage. DumpVisitor is most common implementation. */
    public void setVisitorLibrary(String library) { visitorLibrary = library; }

    /** Sets maximum pending messages one storage visitor will have pending to this client before stalling, waiting for acks. */
    public void setMaxPending(int maxPending) { this.maxPending = maxPending; }

    /** Sets the timeout for each visitor command in milliseconds. */
    public void setTimeoutMs(long timeoutMs) { this.timeoutMs = timeoutMs; }

    /**
     * Setss timeout for the entire visiting session, in milliseconds. -1 implies infinity.
     *
     * If the session takes more time than this to complete, it will automatically
     * be failed with CompletionCode.TIMEOUT.
     * If no session timeout has been explicitly set (or it has been set to -1), visiting will
     * continue until it completes or abort()/destroy() is called on the session instance.
     */
    public void setSessionTimeoutMs(long timeoutMs) { this.sessionTimeoutMs = timeoutMs; }

    /** Sets from timestamp in microseconds. Documents put/updated before this timestamp will not be visited. */
    public void setFromTimestamp(long timestamp) { fromTimestamp = timestamp; }

    /** Sets to timestamp in microseconds. Documents put/updated after this timestamp will not be visited. */
    public void setToTimestamp(long timestamp) { toTimestamp = timestamp; }

    /** Sets whether to visit remove entries. That is, entries saying that some document has been removed. */
    public void visitRemoves(boolean visitRemoves) { this.visitRemoves = visitRemoves; }

    public void setVisitRemoves(boolean visitRemoves) { this.visitRemoves = visitRemoves; }

    /** Sets field set to use. */
    public void fieldSet(String fieldSet) { this.fieldSet = fieldSet; }
    public void setFieldSet(String fieldSet) { this.fieldSet = fieldSet; }

    /** Sets whether to visit inconsistent buckets. */
    public void visitInconsistentBuckets(boolean visitInconsistentBuckets) { this.visitInconsistentBuckets = visitInconsistentBuckets; }

    public void setVisitInconsistentBuckets(boolean visitInconsistentBuckets) { this.visitInconsistentBuckets = visitInconsistentBuckets; }

    /** Sets a visitor library specific parameter. */
    public void setLibraryParameter(String param, String value) {
        libraryParameters.put(param, Utf8.toBytes(value));
    }

    /** Sets a visitor library specific parameter. */
    public void setLibraryParameter(String param, byte [] value) { libraryParameters.put(param, value); }

    /** Sets all visitor library specific parameters. */
    public void setLibraryParameters(Map<String, byte []> params) { libraryParameters = params; }

    /** Sets progress token, which can be used to resume visitor. */
    public void setResumeToken(ProgressToken token) { resumeToken = token; }

    /**
     * Sets filename for reading/storing progress token. If the file exists and
     * contains progress data, visitor should resume visiting from this point.
     */
    public void setResumeFileName(String fileName) { resumeFileName = fileName; }

    /** Sets address for the remote data handler. */
    public void setRemoteDataHandler(String remoteDataHandler) { this.remoteDataHandler = remoteDataHandler; localDataHandler = null; }

    /** Sets local data handler. */
    public void setLocalDataHandler(VisitorDataHandler localDataHandler) { this.localDataHandler = localDataHandler; remoteDataHandler = null; }

    /** Sets control handler. */
    public void setControlHandler(VisitorControlHandler controlHandler) { this.controlHandler = controlHandler; }

    /** Sets the name of the storage cluster route to visit. Default is "storage/cluster.storage". */
    public void setRoute(String route) { setRoute(Route.parse(route)); }

    /** Sets the route to visit. */
    public void setRoute(Route route) { visitRoute = route; }

    /** Returns the name of the storage cluster to visit. */
    // TODO: Document: Where is the default - does this ever return null, or does it return "storage" if input is null?
    public Route getRoute() { return visitRoute; }

    /** Sets the maximum number of documents to visit (max documents returned by the visitor) */
    public void setMaxTotalHits(long max) { maxTotalHits = max; }

    /** Returns the maximum number of documents to visit (max documents returned by the visitor) */
    public long getMaxTotalHits() { return maxTotalHits; }

    public Set<BucketId> getBucketsToVisit() { return bucketsToVisit; }

    public void setBucketsToVisit(Set<BucketId> buckets) { bucketsToVisit = buckets; }

    public int getMaxBucketsPerVisitor() { return maxBucketsPerVisitor; }

    public void setMaxBucketsPerVisitor(int max) { maxBucketsPerVisitor = max; }

    public void setTraceLevel(int traceLevel) { this.traceLevel = traceLevel; }

    public int getTraceLevel() { return traceLevel; }

    public void setPriority(DocumentProtocol.Priority priority) {
        this.priority = priority;
    }

    public boolean skipBucketsOnFatalErrors() { return skipBucketsOnFatalErrors; }

    public void skipBucketsOnFatalErrors(boolean skipBucketsOnFatalErrors) { this.skipBucketsOnFatalErrors = skipBucketsOnFatalErrors; }

    public void slice(int slices, int sliceId) {
        this.slices = slices;
        this.sliceId = sliceId;
    }

    public int getSlices() { return slices; }

    public int getSliceId() { return sliceId; }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
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
        sb.append("  Max total hits:     ").append(maxTotalHits).append('\n');
        sb.append("  Max buckets:        ").append(maxBucketsPerVisitor).append('\n');
        sb.append("  Priority:           ").append(getPriority().toString()).append('\n');
        sb.append(')');

        return sb.toString();
    }

}
