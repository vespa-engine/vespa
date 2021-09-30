// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "documentreply.h"
#include <vespa/documentapi/messagebus/priority.h>
#include <vespa/documentapi/messagebus/documentprotocol.h>

namespace documentapi {

DocumentReply::DocumentReply(uint32_t type) :
    mbus::Reply(),
    _type(type),
    _priority(Priority::PRI_NORMAL_3)
{ }

DocumentReply::~DocumentReply() = default;

const mbus::string&
DocumentReply::getProtocol() const
{
    return DocumentProtocol::NAME;
}

}


