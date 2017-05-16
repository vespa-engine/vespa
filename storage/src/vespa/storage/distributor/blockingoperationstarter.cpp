// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "blockingoperationstarter.h"

namespace storage::distributor {

bool
BlockingOperationStarter::start(const std::shared_ptr<Operation>& operation, Priority priority)
{
    if (operation->isBlocked(_messageTracker)) {
        return true;
    }
    return _starterImpl.start(operation, priority);
}

}
