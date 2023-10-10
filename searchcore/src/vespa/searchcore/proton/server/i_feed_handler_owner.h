// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/common/serialnum.h>

namespace proton {

/**
 * Interface defining the communication needed with the owner of the
 * feed handler.
 */
struct IFeedHandlerOwner {
    virtual ~IFeedHandlerOwner() {}
    virtual void onTransactionLogReplayDone() = 0;
    virtual void enterRedoReprocessState() = 0;
    virtual void onPerformPrune(search::SerialNum flushedSerial) = 0;
    virtual bool getAllowPrune() const = 0;
};

} // namespace proton
