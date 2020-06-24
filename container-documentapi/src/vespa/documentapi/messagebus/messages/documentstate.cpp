// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "documentstate.h"
#include <vespa/document/util/bytebuffer.h>
#include <vespa/documentapi/common.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/vespalib/util/growablebytebuffer.h>

namespace documentapi {

DocumentState::DocumentState()
    : _timestamp(0), _removeEntry(false) {}

DocumentState::DocumentState(const DocumentState& o)
    : _gid(o._gid), _timestamp(o._timestamp), _removeEntry(o._removeEntry)
{
    if (o._docId.get() != 0) {
        _docId = std::make_unique<document::DocumentId>(*o._docId);
    }
}

DocumentState::DocumentState(const document::DocumentId& id, uint64_t timestamp, bool removeEntry)
    : _docId(new document::DocumentId(id)),
      _gid(_docId->getGlobalId()),
      _timestamp(timestamp),
      _removeEntry(removeEntry)
{
}

DocumentState::DocumentState(const document::GlobalId& gid, uint64_t timestamp, bool removeEntry)
    : _gid(gid), _timestamp(timestamp), _removeEntry(removeEntry) {}

DocumentState::DocumentState(document::ByteBuffer& buf)
    : _docId(), _gid(), _timestamp(0), _removeEntry(false)
{
    uint8_t hasDocId;
    buf.getByte(hasDocId);
    if (hasDocId) {
        vespalib::nbostream stream(buf.getBufferAtPos(), buf.getRemaining());
        _docId = std::make_unique<document::DocumentId>(stream);
        buf.incPos(stream.rp());
    }
    const char* gid = buf.getBufferAtPos();
    buf.incPos(document::GlobalId::LENGTH);
    _gid.set(gid);
    buf.getLongNetwork((int64_t&) _timestamp);
    uint8_t b;
    buf.getByte(b);
    _removeEntry = b > 0;
}

DocumentState&
DocumentState::operator=(const DocumentState& other)
{
    _docId.reset();
    if (other._docId) {
        _docId = std::make_unique<document::DocumentId>(*other._docId);
    }
    _gid = other._gid;
    _timestamp = other._timestamp;
    _removeEntry = other._removeEntry;
    return *this;
}

void DocumentState::serialize(vespalib::GrowableByteBuffer &buf) const
{
    if (_docId.get()) {
        buf.putByte(1);
        string str = _docId->toString();
        buf.putBytes(str.c_str(), str.length() + 1);
    } else {
        buf.putByte(0);
    }
    char* buffer = buf.allocate(document::GlobalId::LENGTH);
    memcpy(buffer, _gid.get(), document::GlobalId::LENGTH);

    buf.putLong(_timestamp);
    buf.putByte(_removeEntry ? 1 : 0);
}

} // documentapi
