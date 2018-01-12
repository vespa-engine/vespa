// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "tlcproxy.h"
#include <vespa/searchcore/proton/feedoperation/feedoperation.h>

#include <vespa/log/log.h>
LOG_SETUP(".proton.server.tlcproxy");

using vespalib::nbostream;
using search::transactionlog::Packet;

namespace proton {

void TlcProxy::commit(search::SerialNum serialNum, search::transactionlog::Type type,
                      const vespalib::nbostream &buf, DoneCallback onDone)
{
    Packet::Entry entry(serialNum, type, vespalib::ConstBufferRef(buf.c_str(), buf.size()));
    Packet packet(entry.serializedSize());
    packet.add(entry);
    _tlsDirectWriter.commit(_domain, packet, std::move(onDone));
}

void
TlcProxy::storeOperation(const FeedOperation &op, DoneCallback onDone)
{
    nbostream stream;
    op.serialize(stream);
    LOG(debug, "storeOperation(): serialNum(%" PRIu64 "), type(%u), size(%zu)",
        op.getSerialNum(), (uint32_t)op.getType(), stream.size());
    commit(op.getSerialNum(), (uint32_t)op.getType(), stream, std::move(onDone));
}

}  // namespace proton
