// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "updatedonecontext.h"
#include <vespa/searchcore/proton/common/feedtoken.h>

namespace proton {

UpdateDoneContext::UpdateDoneContext(std::unique_ptr<FeedToken> token, const document::DocumentUpdate::SP &upd)
    : OperationDoneContext(std::move(token)),
      _upd(upd)
{
}

UpdateDoneContext::~UpdateDoneContext() = default;

}  // namespace proton
