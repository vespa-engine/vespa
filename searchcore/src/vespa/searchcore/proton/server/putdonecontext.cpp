// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "putdonecontext.h"
#include <vespa/searchcore/proton/common/feedtoken.h>
#include <vespa/searchcore/proton/common/docid_limit.h>

namespace proton {

PutDoneContext::PutDoneContext(std::unique_ptr<FeedToken> token,
                               const FeedOperation::Type opType,
                               PerDocTypeFeedMetrics &metrics)
    : OperationDoneContext(std::move(token), opType, metrics),
      _lid(0),
      _docIdLimit(nullptr)
{
}

PutDoneContext::~PutDoneContext()
{
    if (_docIdLimit != nullptr) {
        _docIdLimit->bumpUpLimit(_lid + 1);
    }
}

}  // namespace proton
