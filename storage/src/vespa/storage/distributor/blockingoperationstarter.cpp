// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "blockingoperationstarter.h"

namespace storage::distributor {

bool
BlockingOperationStarter::start(const std::shared_ptr<Operation>& operation, Priority priority)
{
    if (operation->isBlocked(_operation_context, _operation_sequencer)) {
        operation->on_blocked();
        return true;
    }
    return _starterImpl.start(operation, priority);
}

}
