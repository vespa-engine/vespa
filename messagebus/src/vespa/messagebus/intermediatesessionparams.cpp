// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/fastos.h>
#include "intermediatesessionparams.h"

namespace mbus {

IntermediateSessionParams::IntermediateSessionParams() :
    _name("intermediate"),
    _broadcastName(true),
    _msgHandler(NULL),
    _replyHandler(NULL)
{
    // empty
}

} // namespace mbus
