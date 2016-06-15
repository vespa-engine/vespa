// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.


#pragma once

#include <vespa/vespalib/util/arrayqueue.hpp>
#include <thread>
#include <mutex>
#include <condition_variable>

namespace fnet {

/**
 * Interface implemented by objects that want to perform synchronous
 * connect initiated by an external thread.
 **/
class ExtConnectable {
protected:
    virtual ~ExtConnectable() {}
public:
    virtual void ext_connect() = 0;
};

/**
 * An object encapsulating a thread responsible for doing synchronous
 * external connect.
 **/
class ConnectThread
{
private:
    using Guard = std::unique_lock<std::mutex>;

    std::mutex                            _lock;
    std::condition_variable               _cond;
    vespalib::ArrayQueue<ExtConnectable*> _queue;
    bool                                  _done;
    std::thread                           _thread;

    void run();

public:
    ConnectThread();
    ~ConnectThread();
    void connect_later(ExtConnectable *conn);
};

} // namespace fnet
