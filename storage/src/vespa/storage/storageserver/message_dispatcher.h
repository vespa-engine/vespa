// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <memory>

namespace storage {

namespace api { class StorageMessage; }

/**
 * Allows for dispatching messages either as a sync or async operation.
 * Semantics:
 *   - dispatch_sync: no immediate thread handoff; try to process in caller thread if possible
 *   - dispatch_async: guaranteed thread handoff; message not processed in caller thread.
 */
class MessageDispatcher {
public:
    virtual ~MessageDispatcher() = default;

    virtual void dispatch_sync(std::shared_ptr<api::StorageMessage> msg) = 0;
    virtual void dispatch_async(std::shared_ptr<api::StorageMessage> msg) = 0;
};

}
