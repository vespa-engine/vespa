// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "emptyreply.h"

namespace {

static mbus::string EmptyReplyProtocolName = "";

} // namespace anon

namespace mbus {

EmptyReply::EmptyReply() { }

const string &
EmptyReply::getProtocol() const {
    return EmptyReplyProtocolName;
}

uint32_t
EmptyReply::getType() const {
    return 0;
}

} // namespace mbus
