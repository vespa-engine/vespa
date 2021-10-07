// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vdslib;

public class VisitorStatistics {
    int bucketsVisited = 0;
    long documentsVisited = 0;
    long bytesVisited = 0;
    long documentsReturned = 0;
    long bytesReturned = 0;
    long secondPassDocumentsReturned = 0;
    long secondPassBytesReturned = 0;

    public void add(VisitorStatistics other) {
        bucketsVisited += other.bucketsVisited;
        documentsVisited += other.documentsVisited;
        bytesVisited += other.bytesVisited;
        documentsReturned += other.documentsReturned;
        bytesReturned += other.bytesReturned;
        secondPassDocumentsReturned += other.secondPassDocumentsReturned;
        secondPassBytesReturned += other.secondPassBytesReturned;
    }

    public int getBucketsVisited() { return bucketsVisited; }
    public void setBucketsVisited(int bucketsVisited) { this.bucketsVisited = bucketsVisited; }

    public long getDocumentsVisited() { return documentsVisited; }
    public void setDocumentsVisited(long documentsVisited) { this.documentsVisited = documentsVisited; }

    public long getBytesVisited() { return bytesVisited; }
    public void setBytesVisited(long bytesVisited) { this.bytesVisited = bytesVisited; }

    public long getDocumentsReturned() { return documentsReturned; }
    public void setDocumentsReturned(long documentsReturned) { this.documentsReturned = documentsReturned; }

    public long getBytesReturned() { return bytesReturned; }
    public void setBytesReturned(long bytesReturned) { this.bytesReturned = bytesReturned; }

    public long getSecondPassDocumentsReturned() { return secondPassDocumentsReturned; }
    public void setSecondPassDocumentsReturned(long secondPassDocumentsReturned) { this.secondPassDocumentsReturned = secondPassDocumentsReturned; }

    public long getSecondPassBytesReturned() { return secondPassBytesReturned; }
    public void setSecondPassBytesReturned(long secondPassBytesReturned) { this.secondPassBytesReturned = secondPassBytesReturned; }

    public String toString() {
        String out =
            "Buckets visited: " + bucketsVisited + "\n" +
            "Documents visited: " + documentsVisited + "\n" +
            "Bytes visited: " + bytesVisited + "\n" +
            "Documents returned: " + documentsReturned + "\n" +
            "Bytes returned: " + bytesReturned + "\n" +
            "Documents returned (2nd pass): " + secondPassDocumentsReturned + "\n" +
            "Bytes returned (2nd pass): " + secondPassBytesReturned + "\n";

        return out;
    }

}
