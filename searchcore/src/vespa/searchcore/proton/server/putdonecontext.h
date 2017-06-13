// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "operationdonecontext.h"

namespace proton
{


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
    uint32_t _lid;
    DocIdLimit *_docIdLimit;

public:
    PutDoneContext(std::unique_ptr<FeedToken> token,
                   const FeedOperation::Type opType,
                   PerDocTypeFeedMetrics &metrics);

    virtual ~PutDoneContext();

    void registerPutLid(uint32_t lid, DocIdLimit *docIdLimit)
    {
        _lid = lid;
        _docIdLimit = docIdLimit;
    }
};


}  // namespace proton
