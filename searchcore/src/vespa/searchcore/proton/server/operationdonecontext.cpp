// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "operationdonecontext.h"
#include <vespa/searchcore/proton/common/feedtoken.h>

namespace proton {

OperationDoneContext::OperationDoneContext(std::unique_ptr<FeedToken> token)
    : _token(std::move(token))
{
}

OperationDoneContext::~OperationDoneContext()
{
    ack();
}

void
OperationDoneContext::ack()
{
    if (_token) {
        std::unique_ptr<FeedToken> token(std::move(_token));
        token->ack();
    }
}

}  // namespace proton
