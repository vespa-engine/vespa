// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.messagebus.protocol;

import com.yahoo.document.BucketId;
import com.yahoo.document.FixedBucketSpaces;

import java.util.*;

public class CreateVisitorMessage extends DocumentMessage {

    private String libName = "DumpVisitor";
    private String instanceId = "";
    private String controlDestination = "";
    private String dataDestination = "";
    private String docSelection = "";
    private String bucketSpace = FixedBucketSpaces.defaultSpace();
    private int maxPendingReplyCount = 8;
    private List<BucketId> buckets = new ArrayList<>();
    private long fromTime = 0;
    private long toTime = 0;
    private boolean visitRemoves = false;
    private String fieldSet = "[all]";
    private boolean visitInconsistentBuckets = false;
    private Map<String, byte[]> params = new TreeMap<>();
    private int version = 42;
    private int ordering = 0;
    private int maxBucketsPerVisitor = 1;

    CreateVisitorMessage() {
        // must be deserialized into
    }

    public CreateVisitorMessage(String libraryName, String instanceId, String controlDestination,
                                String dataDestination) {
        libName = libraryName;
        this.instanceId = instanceId;
        this.controlDestination = controlDestination;
        this.dataDestination = dataDestination;
    }

    public String getLibraryName() {
        return libName;
    }

    public void setLibraryName(String libraryName) {
        libName = libraryName;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    public String getControlDestination() {
        return controlDestination;
    }

    public void setControlDestination(String controlDestination) {
        this.controlDestination = controlDestination;
    }

    public String getDataDestination() {
        return dataDestination;
    }

    public void setDataDestination(String dataDestination) {
        this.dataDestination = dataDestination;
    }

    public String getDocumentSelection() {
        return docSelection;
    }

    public void setDocumentSelection(String documentSelection) {
        docSelection = documentSelection;
    }

    public String getBucketSpace() {
        return bucketSpace;
    }

    public void setBucketSpace(String bucketSpace) {
        this.bucketSpace = bucketSpace;
    }

    public int getMaxPendingReplyCount() {
        return maxPendingReplyCount;
    }

    public void setMaxPendingReplyCount(int count) {
        maxPendingReplyCount = count;
    }

    public Map<String, byte[]> getParameters() {
        return params;
    }

    public void setParameters(Map<String, byte[]> parameters) {
        params = parameters;
    }

    public List<BucketId> getBuckets() {
        return buckets;
    }

    public void setBuckets(List<BucketId> buckets) {
        this.buckets = buckets;
    }

    public boolean getVisitRemoves() {
        return visitRemoves;
    }

    public void setVisitRemoves(boolean visitRemoves) {
        this.visitRemoves = visitRemoves;
    }

    public String getFieldSet() {
        return fieldSet;
    }

    public void setFieldSet(String fieldSet) {
        this.fieldSet = fieldSet;
    }

    public boolean getVisitInconsistentBuckets() {
        return visitInconsistentBuckets;
    }

    public void setVisitInconsistentBuckets(boolean visitInconsistentBuckets) {
        this.visitInconsistentBuckets = visitInconsistentBuckets;
    }

    public void setFromTimestamp(long from) {
        fromTime = from;
    }

    public void setToTimestamp(long to) {
        toTime = to;
    }

    public long getFromTimestamp() {
        return fromTime;
    }

    public long getToTimestamp() {
        return toTime;
    }

    public void setVisitorDispatcherVersion(int version) {
        this.version = version;
    }

    public int getVisitorDispatcherVersion() {
        return version;
    }

    public void setVisitorOrdering(int ordering) {
        this.ordering = ordering;
    }

    public int getVisitorOrdering() {
        return ordering;
    }

    public void setMaxBucketsPerVisitor(int max) {
        this.maxBucketsPerVisitor = max;
    }

    public int getMaxBucketsPerVisitor() {
        return maxBucketsPerVisitor;
    }

    @Override
    public DocumentReply createReply() {
        return new CreateVisitorReply(DocumentProtocol.REPLY_CREATEVISITOR);
    }

    @Override
    public int getApproxSize() {
        return buckets.size() * 8;
    }

    @Override
    public int getType() {
        return DocumentProtocol.MESSAGE_CREATEVISITOR;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder().append("CreateVisitorMessage(");
        if (buckets.size() == 0) {
            sb.append("No buckets");
        } else if (buckets.size() == 1) {
            sb.append("Bucket ").append(buckets.iterator().next().toString());
        } else if (buckets.size() < 65536) {
            sb.append(buckets.size()).append(" buckets:");
            Iterator<BucketId> it = buckets.iterator();
            for (int i = 0; it.hasNext() && i < 3; ++i) {
                sb.append(' ').append(it.next().toString());
            }
            if (it.hasNext()) {
                sb.append(" ...");
            }
        } else {
            sb.append("All buckets");
        }
        if (fromTime != 0 || toTime != 0) {
            sb.append(", time ").append(fromTime).append('-').append(toTime);
        }
        sb.append(", selection '").append(docSelection).append('\'');
        sb.append(", bucket space '").append(bucketSpace).append('\'');
        if (!libName.equals("DumpVisitor")) {
            sb.append(", library ").append(libName);
        }
        if (visitRemoves) {
            sb.append(", including removes");
        }
        sb.append(", get fields: " + fieldSet);
        if (visitInconsistentBuckets) {
            sb.append(", visit inconsistent buckets");
        }
        return sb.append(')').toString();
    }
}
