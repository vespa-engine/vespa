// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.messagebus.protocol;

import com.yahoo.document.DocumentId;
import com.yahoo.document.GlobalId;
import com.yahoo.text.Utf8;
import com.yahoo.vespa.objects.Deserializer;
import com.yahoo.vespa.objects.Serializer;

/**
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
*/
public class DocumentState implements Comparable<DocumentState> {
    private DocumentId docId;
    private GlobalId gid;
    private long timestamp;
    private boolean removeEntry;

    public DocumentState(DocumentId docId, long timestamp, boolean removeEntry) {
        this.docId = docId;
        this.gid = new GlobalId(docId.getGlobalId());
        this.timestamp = timestamp;
        this.removeEntry = removeEntry;
    }

    public DocumentState(GlobalId gid, long timestamp, boolean removeEntry) {
        this.gid = gid;
        this.timestamp = timestamp;
        this.removeEntry = removeEntry;
    }

    public DocumentState(Deserializer buf) {
        byte hasDocId = buf.getByte(null);
        if (hasDocId == (byte) 1) {
            docId = new DocumentId(buf);
        }
        gid = new GlobalId(buf);
        timestamp = buf.getLong(null);
        removeEntry = buf.getByte(null)>0;
    }

    public DocumentId getDocId() {
        return docId;
    }

    public GlobalId getGid() {
        return gid;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public boolean isRemoveEntry() {
        return removeEntry;
    }

    public void serialize(Serializer buf) {
        if (docId != null) {
            buf.putByte(null, (byte) 1);
            docId.serialize(buf);
        } else {
            buf.putByte(null, (byte) 0);
        }
        gid.serialize(buf);
        buf.putLong(null, timestamp);
        buf.putByte(null, (byte)(removeEntry ? 1 : 0));
    }

    public int getSerializedSize() {
        int size = 0;
        if (docId != null) {
            size += Utf8.byteCount(docId.toString()) + 1;
        }
        size += GlobalId.LENGTH;
        size += 8;
        size += 1;
        return size;
    }

    @Override
    public int compareTo(DocumentState state) {
        int comp = gid.compareTo(state.gid);
        if (comp == 0) {
            if (docId != null) {
                if (state.docId != null) {
                    return docId.toString().compareTo(state.docId.toString());
                } else {
                    return 1;
                }
            } else if (state.docId != null){
                return -1;
            }
        }
        return comp;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DocumentState)) return false;

        DocumentState that = (DocumentState) o;

        if (removeEntry != that.removeEntry) return false;
        if (timestamp != that.timestamp) return false;
        if (docId != null ? !docId.equals(that.docId) : that.docId != null) return false;
        return gid.equals(that.gid);
    }

    @Override
    public int hashCode() {
        int result;
        result = (docId != null ? docId.hashCode() : 0);
        result = 31 * result + gid.hashCode();
        result = 31 * result + (int) (timestamp ^ (timestamp >>> 32));
        result = 31 * result + (removeEntry ? 1 : 0);
        return result;
    }

    @Override
    public String toString() {
        return "DocumentState{" +
               "docId=" + docId +
               ", gid=" + gid +
               ", timestamp=" + timestamp +
               ", removeEntry=" + removeEntry +
               '}';
    }

}
