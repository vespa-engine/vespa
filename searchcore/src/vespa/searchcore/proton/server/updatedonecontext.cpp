// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "updatedonecontext.h"
#include <vespa/searchcore/proton/common/feedtoken.h>

namespace proton
{


UpdateDoneContext::UpdateDoneContext(std::unique_ptr<FeedToken> token,
                                     const FeedOperation::Type opType,
                                     PerDocTypeFeedMetrics &metrics,
                                     const document::DocumentUpdate::SP &upd)
    : OperationDoneContext(std::move(token), opType, metrics),
      _upd(upd)
{
}


UpdateDoneContext::~UpdateDoneContext()
{
}


}  // namespace proton
