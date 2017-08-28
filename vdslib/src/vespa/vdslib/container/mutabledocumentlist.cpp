// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "mutabledocumentlist.h"
#include <vespa/document/update/documentupdate.h>
#include <vespa/document/datatype/documenttype.h>
#include <vespa/vespalib/objects/nbostream.h>

using vespalib::compression::CompressionConfig;
using vespalib::nbostream;

namespace vdslib {
MutableDocumentList::MutableDocumentList(const document::DocumentTypeRepo::SP & repo, char* buffer,
                                         uint32_t bufferSize, bool keepexisting)
    : DocumentList(repo, buffer, bufferSize, keepexisting)
{
}

MutableDocumentList::MutableDocumentList(const DocumentList& source, char* buffer, uint32_t bufferSize)
    : DocumentList(source, buffer, bufferSize)
{
}

bool
MutableDocumentList::addOperationList(const OperationList& opl)
{
    for(uint32_t i=0; i < opl.getOperationList().size(); i++){
        switch(opl.getOperationList()[i].opt) {
        case OperationList::Operation::PUT:
            if (!addPut(*opl.getOperationList()[i].document, 0)) return false;
            break;
        case OperationList::Operation::UPDATE:
            if (!addUpdate(*opl.getOperationList()[i].documentUpdate, 0)) return false;
            break;
        case OperationList::Operation::REMOVE:
            if (!addRemove(opl.getOperationList()[i].docId,0)) return false;
            break;
        }
    }
    checkConsistency();
    return true;
}

bool
MutableDocumentList::addPut(const document::Document& doc, Timestamp ts, bool addBody)
{
    uint32_t freePos = _freePtr - _buffer;

    nbostream stream;
    doc.serializeHeader(stream);
    uint32_t headerSize = stream.size();

    if (addBody) {
        doc.serializeBody(stream);
    }

    uint32_t totalSize = stream.size();
    uint32_t bodySize = totalSize - headerSize;

    if (countFree() < totalSize + sizeof(MetaEntry)) {
        return false;
    }

    MetaEntry& entry(getMeta(docCount()));
    entry.timestamp = ts;
    entry.headerPos = freePos - totalSize;
    entry.headerLen = headerSize;
    entry.bodyPos = bodySize ? (freePos - bodySize) : 0;
    entry.bodyLen = bodySize;
    entry.flags = 0;

    if (doc.getType().getFieldsType().getCompressionConfig().type != CompressionConfig::NONE) {
        entry.flags |= MetaEntry::COMPRESSED;
    }

    document::ByteBuffer buffer(_freePtr - totalSize, totalSize);

    buffer.putBytes(stream.peek(), stream.size());
    if (!addBody) {
        entry.flags |= MetaEntry::BODY_STRIPPED;
    }

    // Here we're sure we've completed writing doc, and can update internal
    // info to commit the write.
    _freePtr -= totalSize;
    ++docCount();
    checkConsistency();
    return true;
}

bool
MutableDocumentList::addUpdate(const document::DocumentUpdate& update, Timestamp ts)
{
    vespalib::nbostream os;
    update.serialize42(os);
    uint32_t updsize = os.size();

    if (countFree() - sizeof(MetaEntry) < updsize) {
        return false;
    }

    MetaEntry& entry(getMeta(docCount()));
    entry.timestamp = ts;
    entry.headerPos = (_freePtr - _buffer - updsize);
    entry.headerLen = updsize;
    entry.bodyPos = 0;
    entry.bodyLen = 0;
    entry.flags = 0;

    document::ByteBuffer buffer(_freePtr - updsize, updsize);

    buffer.putBytes(os.c_str(), os.size());
    entry.flags |= MetaEntry::UPDATE_ENTRY;

    // Here we're sure we've completed writing update, and can update internal
    // info to commit the write.
    _freePtr -= updsize;
    ++docCount();
    checkConsistency();
    return true;
}


bool
MutableDocumentList::addRemove(const document::DocumentId& docId, Timestamp ts)
{
    // Creating a document by fetching the first document type declared

    const document::DataType *type(document::DataType::DOCUMENT);
    if (docId.hasDocType()) {
        type = getTypeRepo()->getDocumentType(docId.getDocType());
    }
    document::Document doc(*type, docId);
    nbostream stream;
    doc.serializeHeader(stream);
    uint32_t ssize = stream.size();
    if (countFree() < ssize + sizeof(MetaEntry)) {
        return false;
    }

    MetaEntry& entry(getMeta(docCount()));
    entry.timestamp = ts;
    entry.headerPos = (_freePtr - _buffer - ssize);
    entry.headerLen = ssize;
    entry.bodyPos = 0;
    entry.bodyLen = 0;
    entry.flags = MetaEntry::REMOVE_ENTRY;
    document::ByteBuffer buffer(_freePtr - ssize, ssize);
    doc.serializeHeader(buffer);
        // Here we're sure we've completed writing doc, and can update internal
        // info to commit the remove.
    _freePtr -= ssize;
    ++docCount();
    //printState("Post removing");
    checkConsistency();
    return true;
}


bool
MutableDocumentList::addEntry(const DocumentList::Entry& inEntry)
{
    return addEntry(inEntry, inEntry.getTimestamp());
}

bool
MutableDocumentList::addEntry(const DocumentList::Entry& inEntry, Timestamp ts)
{
    if (countFree() < inEntry.getSerializedSize()) {
        return false;
    }

    MetaEntry& entry(getMeta(docCount()));
    entry.timestamp = ts;
    entry.headerPos = 0;
    entry.headerLen = 0;
    entry.bodyPos = 0;
    entry.bodyLen = 0;
    entry.flags = inEntry.getFlags();

    if ((inEntry.getFlags() & DocumentList::MetaEntry::BODY_IN_HEADER) ||
        (inEntry.getFlags() & DocumentList::MetaEntry::BODY_STRIPPED))
    {
        DocumentList::Entry::BufferPosition header = inEntry.getRawHeader();
        document::ByteBuffer buffer(_freePtr - header.second, header.second);

        entry.headerPos = (_freePtr - _buffer - header.second);
        entry.headerLen = header.second;
        entry.bodyPos = 0;
        entry.bodyLen = 0;
        buffer.putBytes(header.first, header.second);

        // Here we're sure we've completed writing doc, and can update internal
        // info to commit the write.
        _freePtr -= header.second;
    } else {
        DocumentList::Entry::BufferPosition header = inEntry.getRawHeader();
        DocumentList::Entry::BufferPosition body = inEntry.getRawBody();
        document::ByteBuffer buffer(_freePtr - (header.second + body.second), (header.second + body.second));

        entry.headerPos = (_freePtr - _buffer - header.second);
        entry.headerLen = header.second;
        entry.bodyPos = (_freePtr - _buffer - header.second - body.second);
        entry.bodyLen = body.second;
        buffer.putBytes(body.first, body.second);
        buffer.putBytes(header.first, header.second);

        // Here we're sure we've completed writing doc, and can update internal
        // info to commit the write.
        _freePtr -= (header.second + body.second);
    }

    ++docCount();
    checkConsistency();
    return true;
}

} // vdslib
