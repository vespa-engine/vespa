// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP(".emptyreply");

#include "emptyreply.h"

namespace {

static mbus::string EmptyReplyProtocolName = "";

} // namespace anon

namespace mbus {

EmptyReply::EmptyReply()
{
    // empty
}

const string &
EmptyReply::getProtocol() const
{
    return EmptyReplyProtocolName;
}

uint32_t
EmptyReply::getType() const
{
    return 0;
}

Blob
EmptyReply::encode() const
{
    return Blob(0);
}

} // namespace mbus
