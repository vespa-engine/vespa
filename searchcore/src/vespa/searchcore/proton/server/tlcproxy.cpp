// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "tlcproxy.h"
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/searchlib/common/bitvector.h>
#include <vespa/document/bucket/bucketid.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/log/log.h>
LOG_SETUP(".proton.server.tlcproxy");

using vespalib::nbostream;

namespace proton {

void TlcProxy::commit(search::SerialNum serialNum, search::transactionlog::Type type, const vespalib::nbostream &buf)
{
    search::transactionlog::Packet::Entry
        entry(serialNum, type, vespalib::ConstBufferRef(buf.c_str(), buf.size()));
    search::transactionlog::Packet packet;
    packet.add(entry);
    packet.close();
    if (_tlsDirectWriter != NULL) {
        _tlsDirectWriter->commit(_session.getDomain(), packet);
    } else {
        if (!_session.commit(vespalib::ConstBufferRef(packet.getHandle().c_str(), packet.getHandle().size()))) {
            throw vespalib::IllegalStateException(vespalib::make_string(
                        "Failed to commit packet %" PRId64
                        " to TLS (type = %d, size = %d).",
                        entry.serial(), type, (uint32_t)buf.size()));
        }
    }
}

void
TlcProxy::storeOperation(const FeedOperation &op)
{
    nbostream stream;
    op.serialize(stream);
    LOG(debug, "storeOperation(): serialNum(%" PRIu64 "), type(%u), size(%zu)",
        op.getSerialNum(), (uint32_t)op.getType(), stream.size());
    commit(op.getSerialNum(), (uint32_t)op.getType(), stream);
}

}  // namespace proton
