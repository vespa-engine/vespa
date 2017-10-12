// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/common/idestructorcallback.h>
#include <vespa/searchcore/proton/feedoperation/feedoperation.h>

namespace proton {

class FeedToken;

/**
 * Context class for document operations that acks operation when
 * instance is destroyed. Typically a shared pointer to an instance is
 * passed around to multiple worker threads that performs portions of
 * a larger task before dropping the shared pointer, triggering the
 * ack when all worker threads have completed.
 */
class OperationDoneContext : public search::IDestructorCallback
{
    std::unique_ptr<FeedToken> _token;
protected:
    void ack();

public:
    OperationDoneContext(std::unique_ptr<FeedToken> token);

    ~OperationDoneContext() override;
    FeedToken *getToken() { return _token.get(); }
};


}  // namespace proton
