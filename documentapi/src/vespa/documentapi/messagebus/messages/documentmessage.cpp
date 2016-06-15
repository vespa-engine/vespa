// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP(".documentmessage");

#include <vespa/documentapi/messagebus/messages/documentmessage.h>
#include <vespa/documentapi/messagebus/documentprotocol.h>
#include <vespa/documentapi/messagebus/priority.h>

namespace documentapi {

DocumentMessage::DocumentMessage() :
    mbus::Message(),
    _priority(Priority::PRI_NORMAL_3),
    _loadType(LoadType::DEFAULT),
    _approxSize(1024)
{
    // empty
}

mbus::Reply::UP
DocumentMessage::createReply() const
{
    mbus::Reply::UP ret(doCreateReply().release());
    LOG_ASSERT(ret.get() != NULL);
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
