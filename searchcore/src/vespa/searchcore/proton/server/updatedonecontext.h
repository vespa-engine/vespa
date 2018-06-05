// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "operationdonecontext.h"
#include <vespa/document/update/documentupdate.h>
#include <future>

namespace document { class Document; }

namespace proton {

/**
 * Context class for document update operations that acks operation when
 * instance is destroyed. Typically a shared pointer to an instance is
 * passed around to multiple worker threads that performs portions of
 * a larger task before dropping the shared pointer, triggering the
 * ack when all worker threads have completed.
 */
class UpdateDoneContext : public OperationDoneContext
{
    document::DocumentUpdate::SP _upd;
    std::shared_future<std::unique_ptr<const document::Document>> _doc;
public:
    UpdateDoneContext(FeedToken token, const document::DocumentUpdate::SP &upd);
    ~UpdateDoneContext() override;

    const document::DocumentUpdate &getUpdate() { return *_upd; }
    void setDocument(std::shared_future<std::unique_ptr<const document::Document>> doc);
};


}  // namespace proton
