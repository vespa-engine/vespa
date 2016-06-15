// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/fastos.h>
#include <vespa/documentapi/messagebus/priority.h>
#include <vespa/documentapi/messagebus/messages/documentreply.h>

namespace documentapi {

DocumentReply::DocumentReply(uint32_t type) :
    mbus::Reply(),
    _type(type),
    _priority(Priority::PRI_NORMAL_3)
{
    // empty
}

const mbus::string&
DocumentReply::getProtocol() const
{
    return DocumentProtocol::NAME;
}

}


