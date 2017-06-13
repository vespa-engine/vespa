// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vdslib;

import com.yahoo.compress.CompressionType;
import com.yahoo.document.*;
import com.yahoo.document.serialization.DocumentSerializer;
import com.yahoo.document.serialization.DocumentSerializerFactory;
import com.yahoo.vespa.objects.Serializer;

import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A list of document operations.
 *
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
 */
public class DynamicDocumentList extends DocumentList {
    private List<Entry> entries;

    DynamicDocumentList(List<Entry> entries) {
        //the entries themselves are of course still modifiable, this is just an internal safeguard:
        this.entries = Collections.unmodifiableList(entries);
    }

    DynamicDocumentList(Entry entry) {
        List<Entry> list = new ArrayList<>(1);
        list.add(entry);
        BucketIdFactory factory = new BucketIdFactory();
        //the entry itself is of course still modifiable, this is just an internal safeguard:
        this.entries = Collections.unmodifiableList(list);
    }

    @Override
    public Entry get(int index) throws ArrayIndexOutOfBoundsException {
        return entries.get(index);
    }

    @Override
    public int size() {
        return entries.size();
    }

    @Override
    public int getApproxByteSize() {
        int size = 4;
        for (Entry entry : entries) {
            if (entry.getDocumentOperation() instanceof DocumentPut) {
                Document doc = ((DocumentPut)entry.getDocumentOperation()).getDocument();
                size += MetaEntry.SIZE + doc.getSerializedSize();
            } else if (entry.getDocumentOperation() instanceof DocumentUpdate) {
                //TODO: Implement getSerializedSize() for DocumentUpdate!!!
                size += MetaEntry.SIZE + 1024;
            } else if (entry.getDocumentOperation() instanceof DocumentRemove) {
                //TODO: Implement getSerializedSize() for DocumentRemove!!!
                size += MetaEntry.SIZE + 64;
            }
        }
        return size;
    }

    @Override
    public void serialize(Serializer buf) {
        if (buf instanceof DocumentSerializer) {
            serializeInternal((DocumentSerializer) buf);
        } else {
            DocumentSerializer serializer = DocumentSerializerFactory.create42();
            serializeInternal(serializer);
            serializer.getBuf().getByteBuffer().flip();
            buf.put(null, serializer.getBuf().getByteBuffer());
        }
    }
    private void serializeInternal(DocumentSerializer buf) {
        ByteOrder originalOrder = buf.getBuf().order();
        buf.getBuf().order(ByteOrder.LITTLE_ENDIAN);
        //save the position before the size
        int posAtBeginning = buf.getBuf().position();

        //write the number of entries
        buf.putInt(null, entries.size());

        //create a list of metaentries, one for each entry
        List<MetaEntry> metaEntries = new ArrayList<MetaEntry>(entries.size());

        //jump past the meta block, we will serialize this afterwards when we know sizes and positions
        byte[] bogusEntry = new byte[entries.size() * MetaEntry.SIZE];
        buf.put(null, bogusEntry);

        for (Entry entry : entries) {
            MetaEntry metaEntry = new MetaEntry();
            metaEntries.add(metaEntry);

            // is this a remove? in that case, set this flag
            if (entry.isRemoveEntry()) metaEntry.flags |= MetaEntry.REMOVE_ENTRY;
            // is the body stripped? in that case, set this flag
            if (entry.isBodyStripped()) metaEntry.flags |= MetaEntry.BODY_STRIPPED;
            // is this an update? in that case, set this flag
            if (entry.getDocumentOperation() instanceof DocumentUpdate) metaEntry.flags |= MetaEntry.UPDATE_ENTRY;
            // is this a document? in that case, try to set the timestamp
            if (entry.getDocumentOperation() instanceof DocumentPut) {
                Document doc = ((DocumentPut)entry.getDocumentOperation()).getDocument();
                Long lastModified = doc.getLastModified();
                if (lastModified != null) {
                    metaEntry.timestamp = lastModified;
                }

                if (doc.getDataType().getHeaderType().getCompressionConfig() != null
                        && doc.getDataType().getHeaderType().getCompressionConfig().type != CompressionType.NONE) {
                    metaEntry.flags |= MetaEntry.COMPRESSED;
                }
                if (doc.getDataType().getBodyType().getCompressionConfig() != null
                        && doc.getDataType().getBodyType().getCompressionConfig().type != CompressionType.NONE) {
                    metaEntry.flags |= MetaEntry.COMPRESSED;
                }
            }

            metaEntry.headerPos = buf.getBuf().position() - posAtBeginning;

            buf.getBuf().order(ByteOrder.BIG_ENDIAN);
            if (entry.getDocumentOperation() instanceof DocumentPut) {
                Document doc = ((DocumentPut)entry.getDocumentOperation()).getDocument();
                //serialize document and save length:
                doc.serializeHeader(buf);
            } else if (entry.getDocumentOperation() instanceof DocumentUpdate) {
                DocumentUpdate docUp = (DocumentUpdate) entry.getDocumentOperation();
                docUp.serialize(buf);
            } else if (entry.getDocumentOperation() instanceof DocumentRemove) {
                new Document(DataType.DOCUMENT, entry.getDocumentOperation().getId()).serialize(buf);
            } else {
                throw new IllegalArgumentException("Can not handle class " + entry.getDocumentOperation().getClass().getName());
            }

            metaEntry.headerLen = buf.getBuf().position() - metaEntry.headerPos - posAtBeginning;

            if (entry.getDocumentOperation() instanceof DocumentPut) {
                metaEntry.bodyPos = buf.getBuf().position() - posAtBeginning;
                Document doc = ((DocumentPut)entry.getDocumentOperation()).getDocument();
                doc.serializeBody(buf);
                metaEntry.bodyLen = buf.getBuf().position() - metaEntry.bodyPos - posAtBeginning;
            } else {
                metaEntry.bodyPos = 0;
                metaEntry.bodyLen = 0;
            }
            buf.getBuf().order(ByteOrder.LITTLE_ENDIAN);

        }
        //save position after payload:
        int posAfterEntries = buf.getBuf().position();
        //go to beginning (after length) to serialize metaentries:
        buf.getBuf().position(posAtBeginning + 4);
        //serialize metaentries
        for (MetaEntry metaEntry : metaEntries) {
            metaEntry.serialize(buf.getBuf());
        }
        //set position to after payload:
        buf.getBuf().position(posAfterEntries);
        buf.getBuf().order(originalOrder);
    }
}
