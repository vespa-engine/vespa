// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "putdonecontext.h"
#include <vespa/document/fieldvalue/document.h>
#include <vespa/searchcore/proton/common/docid_limit.h>
#include <vespa/searchcore/proton/reference/i_gid_to_lid_change_handler.h>

using document::Document;

namespace proton {

PutDoneContext::PutDoneContext(FeedToken token, IPendingLidTracker::Token uncommitted,
                               std::shared_ptr<const Document> doc,
                               uint32_t lid)
    : OperationDoneContext(std::move(token)),
      _uncommitted(std::move(uncommitted)),
      _lid(lid),
      _docIdLimit(nullptr),
      _doc(std::move(doc))
{
}

PutDoneContext::~PutDoneContext()
{
    if (_docIdLimit != nullptr) {
        _docIdLimit->bumpUpLimit(_lid + 1);
    }
}

}  // namespace proton
