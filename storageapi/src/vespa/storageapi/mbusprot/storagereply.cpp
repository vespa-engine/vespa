// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "storagereply.h"
#include "storagecommand.h"
#include <vespa/vespalib/util/exceptions.h>

using vespalib::alloc::Alloc;
using vespalib::IllegalStateException;

namespace storage::mbusprot {

StorageReply::StorageReply(mbus::BlobRef data, const ProtocolSerialization& serializer)
    : _serializer(&serializer),
      _sz(data.size()),
      _buffer(Alloc::alloc(_sz)),
      _mbusType(0),
      _reply()
{
    memcpy(_buffer.get(), data.data(), _sz);
    document::ByteBuffer buf(data.data(), _sz);
    buf.getIntNetwork(reinterpret_cast<int32_t&>(_mbusType));
}

StorageReply::StorageReply(api::StorageReply::SP reply)
    : _serializer(0),
      _sz(0),
      _buffer(),
      _mbusType(reply->getType().getId()),
      _reply(std::move(reply))
{}

StorageReply::~StorageReply() = default;

void
StorageReply::deserialize() const
{
    if (_reply.get()) return;
    StorageReply& reply(const_cast<StorageReply&>(*this));
    mbus::Message::UP msg(reply.getMessage());
    if (msg.get() == 0) {
        throw IllegalStateException("Cannot deserialize storage reply before message have been set", VESPA_STRLOC);
    }
    const StorageCommand* cmd(dynamic_cast<const StorageCommand*>(msg.get()));
    reply.setMessage(std::move(msg));
    if (cmd == 0) {
        throw IllegalStateException("Storage reply get message did not return a storage command", VESPA_STRLOC);
    }
    mbus::BlobRef blobRef(static_cast<char *>(_buffer.get()), _sz);
    _reply = _serializer->decodeReply(blobRef, *cmd->getCommand())->getReply();
    Alloc().swap(_buffer);
}

}
