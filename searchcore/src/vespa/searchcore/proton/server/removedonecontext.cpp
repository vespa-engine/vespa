// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "removedonecontext.h"

namespace proton {

RemoveDoneContext::RemoveDoneContext(IDestructorCallback::SP token, IPendingLidTracker::Token uncommitted)
    : OperationDoneContext(std::move(token)),
      _uncommitted(std::move(uncommitted))
{
}

RemoveDoneContext::~RemoveDoneContext()
{
}

}  // namespace proton
