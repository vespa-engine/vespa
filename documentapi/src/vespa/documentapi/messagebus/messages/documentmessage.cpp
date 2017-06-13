// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "documentmessage.h"
#include <vespa/documentapi/messagebus/documentprotocol.h>
#include <cassert>

namespace documentapi {

DocumentMessage::DocumentMessage() :
    mbus::Message(),
    _priority(Priority::PRI_NORMAL_3),
    _loadType(LoadType::DEFAULT),
    _approxSize(1024)
{}

mbus::Reply::UP
DocumentMessage::createReply() const
{
    mbus::Reply::UP ret(doCreateReply().release());
    assert(ret.get() != nullptr);
    return ret;
}

const mbus::string&
DocumentMessage::getProtocol() const
{
    return DocumentProtocol::NAME;
}

uint32_t
DocumentMessage::getApproxSize() const
{
    return _approxSize;
}

}
