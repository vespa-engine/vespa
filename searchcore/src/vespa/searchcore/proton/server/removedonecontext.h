// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "operationdonecontext.h"
#include <vespa/searchcore/proton/common/ipendinglidtracker.h>

namespace proton {

/**
 * Context class for document removes that acks remove
 * when instance is destroyed. Typically a shared pointer to an
 * instance is passed around to multiple worker threads that performs
 * portions of a larger task before dropping the shared pointer,
 * triggering the ack when all worker threads have completed.
 */
class RemoveDoneContext : public OperationDoneContext
{
    IPendingLidTracker::Token _uncommitted;

public:
    RemoveDoneContext(std::shared_ptr<feedtoken::IState>, std::shared_ptr<vespalib::IDestructorCallback> done_callback, IPendingLidTracker::Token uncommitted);
    ~RemoveDoneContext() override;
};

}  // namespace proton
