// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "documentmessage.h"
#include <vespa/documentapi/messagebus/documentprotocol.h>

namespace documentapi {

DocumentMessage::DocumentMessage() :
    mbus::Message(),
    _priority(Priority::PRI_NORMAL_3),
    _approxSize(1024)
{}

DocumentMessage::~DocumentMessage() = default;

mbus::Reply::UP
DocumentMessage::createReply() const
{
    return doCreateReply();
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
