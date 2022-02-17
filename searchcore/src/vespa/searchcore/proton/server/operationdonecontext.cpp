// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "operationdonecontext.h"
#include <vespa/searchcore/proton/common/feedtoken.h>

namespace proton {

OperationDoneContext::OperationDoneContext(std::shared_ptr<feedtoken::IState> token, std::shared_ptr<vespalib::IDestructorCallback> done_callback)
    : _token(std::move(token)),
      _done_callback(std::move(done_callback))
{
}

OperationDoneContext::~OperationDoneContext() = default;

bool
OperationDoneContext::is_replay() const
{
    return (!_token || _token->is_replay());
}


}  // namespace proton
