// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/util/idestructorcallback.h>
#include <vespa/searchcore/proton/common/feedtoken.h>

namespace proton {

/**
 * Context class for document operations that acks operation when
 * instance is destroyed. Typically a shared pointer to an instance is
 * passed around to multiple worker threads that performs portions of
 * a larger task before dropping the shared pointer, triggering the
 * ack when all worker threads have completed.
 */
class OperationDoneContext : public vespalib::IDestructorCallback
{
    FeedToken _token;
protected:
    void ack();
    FeedToken steal() { return std::move(_token); }

public:
    OperationDoneContext(FeedToken token);

    ~OperationDoneContext() override;
    bool hasToken() const { return static_cast<bool>(_token); }
};


}  // namespace proton
