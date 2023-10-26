// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <memory>

namespace mbus {

class Reply;

/**
 * This interface is implemented by application components that want
 * to handle incoming replies received from either an
 * IntermediateSession or a SourceSession.
 **/
class IReplyHandler
{
protected:
    IReplyHandler() = default;
public:
    IReplyHandler(const IReplyHandler &) = delete;
    IReplyHandler & operator = (const IReplyHandler &) = delete;
    virtual ~IReplyHandler() {}

    /**
     * This method is invoked by messagebus to deliver a Reply.
     *
     * @param reply the Reply being delivered
     **/
    virtual void handleReply(std::unique_ptr<Reply> reply) = 0;
};

} // namespace mbus

