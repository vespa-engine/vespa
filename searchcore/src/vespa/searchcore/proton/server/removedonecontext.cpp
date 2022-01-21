// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "removedonecontext.h"

namespace proton {

RemoveDoneContext::RemoveDoneContext(std::shared_ptr<feedtoken::IState> token, std::shared_ptr<IDestructorCallback> done_callback, IPendingLidTracker::Token uncommitted)
    : OperationDoneContext(std::move(token), std::move(done_callback)),
      _uncommitted(std::move(uncommitted))
{
}

RemoveDoneContext::~RemoveDoneContext()
{
}

}  // namespace proton
