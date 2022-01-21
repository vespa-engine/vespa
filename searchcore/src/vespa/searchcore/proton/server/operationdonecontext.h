// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/util/idestructorcallback.h>

namespace proton::feedtoken { class IState; }

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
public:
    OperationDoneContext(std::shared_ptr<feedtoken::IState> token, std::shared_ptr<IDestructorCallback> done_callback);

    ~OperationDoneContext() override;
    bool is_replay() const;
private:
    std::shared_ptr<feedtoken::IState> _token;
    std::shared_ptr<vespalib::IDestructorCallback> _done_callback;
};

}  // namespace proton
