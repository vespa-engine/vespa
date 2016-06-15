// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <memory>
#include "reply.h"

namespace mbus {

/**
 * This interface is implemented by application components that want
 * to handle incoming replies received from either an
 * IntermediateSession or a SourceSession.
 **/
class IReplyHandler
{
public:
    virtual ~IReplyHandler() {}

    /**
     * This method is invoked by messagebus to deliver a Reply.
     *
     * @param reply the Reply being delivered
     **/
    virtual void handleReply(Reply::UP reply) = 0;
};

} // namespace mbus

