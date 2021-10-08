// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "operationdonecontext.h"

namespace proton {

OperationDoneContext::OperationDoneContext(IDestructorCallback::SP token)
    : _token(std::move(token))
{
}

OperationDoneContext::~OperationDoneContext() = default;

}  // namespace proton
