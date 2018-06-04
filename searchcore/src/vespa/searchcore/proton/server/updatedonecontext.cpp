// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "updatedonecontext.h"
#include <vespa/document/fieldvalue/document.h>

using document::Document;

namespace proton {

UpdateDoneContext::UpdateDoneContext(FeedToken token, const document::DocumentUpdate::SP &upd)
    : OperationDoneContext(std::move(token)),
      _upd(upd),
      _doc()
{
}

UpdateDoneContext::~UpdateDoneContext() = default;

void
UpdateDoneContext::setDocument(std::shared_future<std::unique_ptr<const Document>> doc)
{
    _doc = std::move(doc);
}

}  // namespace proton
