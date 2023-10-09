// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class storage::VisitorMessageSession
 */
#pragma once

#include <vespa/messagebus/result.h>

namespace documentapi {
    class DocumentMessage;
}

namespace storage {

struct VisitorMessageSession {
    using UP = std::unique_ptr<VisitorMessageSession>;

    virtual ~VisitorMessageSession() {}

    virtual mbus::Result send(std::unique_ptr<documentapi::DocumentMessage>) = 0;

    /** @return Returns the number of pending messages this session has. */
    virtual uint32_t pending() = 0;

};

} // storage

