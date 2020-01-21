// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "storagereply.h"
#include "storagecommand.h"
#include <ostream>

namespace storage::api {

StorageReply::StorageReply(const StorageCommand& cmd, ReturnCode code)
    : StorageMessage(cmd.getType().getReplyType(), cmd.getMsgId()),
      _result(code)
{
    setPriority(cmd.getPriority());
    if (cmd.getAddress()) {
        setAddress(*cmd.getAddress());
    }
    setTrace(cmd.getTrace());
    setTransportContext(cmd.getTransportContext());
}

StorageReply::~StorageReply() = default;

void
StorageReply::print(std::ostream& out, bool verbose,
                    const std::string& indent) const
{
    (void) verbose; (void) indent;
    out << "StorageReply(" << _type.getName() << ", " << _result << ")";
}

}
