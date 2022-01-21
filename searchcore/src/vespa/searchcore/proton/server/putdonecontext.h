// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "operationdonecontext.h"
#include <vespa/searchcore/proton/common/ipendinglidtracker.h>
#include <vespa/document/base/globalid.h>
#include <vespa/searchlib/common/serialnum.h>

namespace document { class Document; }

namespace proton {

class DocIdLimit;

/**
 * Context class for document put operations that acks operation when
 * instance is destroyed. Typically a shared pointer to an instance is
 * passed around to multiple worker threads that performs portions of
 * a larger task before dropping the shared pointer, triggering the
 * ack when all worker threads have completed.
 */
class PutDoneContext : public OperationDoneContext
{
    IPendingLidTracker::Token _uncommitted;
    uint32_t                  _lid;
    DocIdLimit               *_docIdLimit;
    std::shared_ptr<const document::Document> _doc;

public:
    PutDoneContext(std::shared_ptr<feedtoken::IState> token,
                   std::shared_ptr<vespalib::IDestructorCallback> done_callback,
                   IPendingLidTracker::Token uncommitted,
                   std::shared_ptr<const document::Document> doc, uint32_t lid);
    ~PutDoneContext() override;

    void registerPutLid(DocIdLimit *docIdLimit) { _docIdLimit = docIdLimit; }
};

}  // namespace proton
