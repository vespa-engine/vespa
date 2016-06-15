// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.persistence.dummy;

import com.yahoo.collections.Pair;
import com.yahoo.document.BucketId;
import com.yahoo.document.BucketIdFactory;
import com.yahoo.document.Document;
import com.yahoo.document.DocumentId;
import com.yahoo.persistence.spi.BucketInfo;
import com.yahoo.persistence.spi.DocEntry;
import com.yahoo.persistence.spi.result.GetResult;

import java.util.ArrayList;
import java.util.List;

/**
 * Class used by DummyPersistence to store its contents.
 */
public class BucketContents {
    List<DocEntry> entries = new ArrayList<DocEntry>();

    BucketInfo.ActiveState active;

    public void setActiveState(BucketInfo.ActiveState state) {
        active = state;
    }

    public boolean isActive() {
        return active == BucketInfo.ActiveState.ACTIVE;
    }

    public BucketInfo getBucketInfo() {
        int count = 0;
        int meta = 0;
        int checksum = 0;

        for (DocEntry e : entries) {
            if (e.getType() == DocEntry.Type.PUT_ENTRY) {
                ++count;
                checksum ^= e.getTimestamp();
            }
            ++meta;
        }


        return new BucketInfo(checksum,
                              count,
                              meta,
                              meta,
                              meta,
                              BucketInfo.ReadyState.READY,
                              active);
    }

    public void put(long timestamp, Document doc) {
        for (DocEntry e : entries) {
            if (e.getDocumentId().equals(doc.getId())) {
                if (e.getTimestamp() > timestamp) {
                    return;
                }

                entries.remove(e);
                break;
            }
        }

        entries.add(new DocEntry(timestamp, doc));
    }

    public boolean remove(long timestamp, DocumentId docId) {
        DocEntry found = null;

        for (DocEntry e : entries) {
            if (
                    e.getType() == DocEntry.Type.PUT_ENTRY &&
                    e.getDocumentId().equals(docId) &&
                    e.getTimestamp() <= timestamp)
            {
                found = e;
                entries.remove(e);
                break;
            }
        }

        entries.add(new DocEntry(timestamp, docId));
        return found != null;
    }

    public GetResult get(DocumentId id) {
        for (DocEntry e : entries) {
            if (e.getType() == DocEntry.Type.PUT_ENTRY && e.getDocumentId().equals(id)) {
                return new GetResult(e.getDocument(), e.getTimestamp());
            }
        }

        return new GetResult();
    }

    public Pair<BucketContents, BucketContents> split(BucketId target1, BucketId target2) {
        BucketContents a = new BucketContents();
        BucketContents b = new BucketContents();

        for (DocEntry e : entries) {
            BucketId bucketId = new BucketIdFactory().getBucketId(e.getDocumentId());
            if (target1.contains(bucketId)) {
                a.entries.add(e);
            } else {
                b.entries.add(e);
            }
        }

        return new Pair<BucketContents, BucketContents>(a, b);
    }

    public BucketContents() {}

    public BucketContents(BucketContents a, BucketContents b) {
        if (a != null) {
            entries.addAll(a.entries);
        }
        if (b != null) {
            entries.addAll(b.entries);
        }
    }

}
