// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "handler.h"
#include "provider.h"
#include "closeable.h"
#include <vespa/vespalib/util/gate.h>
#include <vector>

namespace vbench {

/**
 * Dispatch objects between threads. Objects received through the
 * Handler interface will be passed along to Components requesting
 * objects through the Provider interface. If there are no components
 * currently waiting for objects, the objects will be passed along to
 * a predefined fallback handler instead. A closed dispatcher will
 * provide nil objects and handle incoming objects by deleting them.
 **/
template <typename T>
class Dispatcher : public Handler<T>,
                   public Provider<T>,
                   public Closeable
{
private:
    struct ThreadState {
        std::unique_ptr<T> object;
        vespalib::Gate   gate;
    };

    Handler<T>               &_fallback;
    mutable std::mutex        _lock;
    std::vector<ThreadState*> _threads;
    bool                      _closed;

public:
    explicit Dispatcher(Handler<T> &fallback);
    ~Dispatcher() override;
    bool waitForThreads(size_t threads, size_t pollCnt) const;
    void close() override;
    void handle(std::unique_ptr<T> obj) override;
    std::unique_ptr<T> provide() override;
};

} // namespace vbench

#include "dispatcher.hpp"

