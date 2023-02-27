// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "context.h"

namespace mbus {

/**
 * This interface is implemented by application components that require special
 * handling when discarding a message with a non-empty callstack.
 */
class IDiscardHandler
{
public:
    /**
     * Virtual destructor required for inheritance.
     */
    virtual ~IDiscardHandler() = default;

    /**
     * This method is invoked by message bus when a routable is being
     * dicarded. This is invoked INSTEAD of the corresponding {@link
     * ReplyHandler}.
     *
     * @param ctx The context of the discarded reply.
     */
    virtual void handleDiscard(Context ctx) = 0;
};

} // namespace mbus

