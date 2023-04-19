// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vdslib;

public class VisitorStatistics {

    int bucketsVisited = 0;
    long documentsVisited = 0;
    long bytesVisited = 0;
    long documentsReturned = 0;
    long bytesReturned = 0;

    public void add(VisitorStatistics other) {
        bucketsVisited += other.bucketsVisited;
        documentsVisited += other.documentsVisited;
        bytesVisited += other.bytesVisited;
        documentsReturned += other.documentsReturned;
        bytesReturned += other.bytesReturned;
    }

    public int getBucketsVisited() { return bucketsVisited; }
    public void setBucketsVisited(int bucketsVisited) { this.bucketsVisited = bucketsVisited; }

    /**
     * Returns the number of documents matching the document selection in the backend that
     * has been passed to the client-specified visitor instance (dumpvisitor, searchvisitor etc).
     */
    public long getDocumentsVisited() { return documentsVisited; }
    public void setDocumentsVisited(long documentsVisited) { this.documentsVisited = documentsVisited; }

    public long getBytesVisited() { return bytesVisited; }
    public void setBytesVisited(long bytesVisited) { this.bytesVisited = bytesVisited; }

    /**
     * Returns the number of documents returned to the visitor client by the backend. This number may
     * be lower than that returned by getDocumentsVisited() since the client-specified visitor
     * instance may further have filtered the set of documents returned by the backend.
     */
    public long getDocumentsReturned() { return documentsReturned; }
    public void setDocumentsReturned(long documentsReturned) { this.documentsReturned = documentsReturned; }

    public long getBytesReturned() { return bytesReturned; }
    public void setBytesReturned(long bytesReturned) { this.bytesReturned = bytesReturned; }

    @Override
    public String toString() {
        return
            "Buckets visited: " + bucketsVisited + "\n" +
            "Documents visited: " + documentsVisited + "\n" +
            "Bytes visited: " + bytesVisited + "\n" +
            "Documents returned: " + documentsReturned + "\n" +
            "Bytes returned: " + bytesReturned + "\n";
    }

}
