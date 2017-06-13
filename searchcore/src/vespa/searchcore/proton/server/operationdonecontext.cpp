// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "operationdonecontext.h"
#include <vespa/searchcore/proton/common/feedtoken.h>

namespace proton {

OperationDoneContext::OperationDoneContext(std::unique_ptr<FeedToken> token,
                                           const FeedOperation::Type opType,
                                           PerDocTypeFeedMetrics &metrics)
    : _token(std::move(token)),
      _opType(opType),
      _metrics(metrics)
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
        token->ack(_opType, _metrics);
    }
}

bool
OperationDoneContext::shouldTrace(uint32_t traceLevel)
{
    return _token ? _token->shouldTrace(traceLevel) : false;
}

}  // namespace proton
