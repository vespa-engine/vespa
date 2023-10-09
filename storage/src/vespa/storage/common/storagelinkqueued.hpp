// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "storagelinkqueued.h"
#include <vespa/storageframework/generic/thread/thread.h>
#include <vespa/storageframework/generic/component/component.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <sstream>
#include <chrono>
#include <cassert>

namespace storage {

template<typename Message>
void
StorageLinkQueued::Dispatcher<Message>::shutdown() {
    if (_thread) {
        _thread->interrupt();
        {
            std::lock_guard guard(_sync);
            _syncCond.notify_one();
        }
        _thread->join();
        _thread.reset();
    }
}

template<typename Message>
StorageLinkQueued::Dispatcher<Message>::Dispatcher(StorageLinkQueued& parent, unsigned int maxQueueSize, bool replyDispatcher)
    : _parent(parent),
      _maxQueueSize(maxQueueSize),
      _sync(),
      _syncCond(),
      _messages(),
      _replyDispatcher(replyDispatcher)
{
    std::ostringstream name;
    name << "Queued storage " << (_replyDispatcher ? "up" : "down")
         << "link - " << _parent.getName();
    _component = std::make_unique<framework::Component>(parent.getComponentRegister(), name.str());
}

template<typename Message>
StorageLinkQueued::Dispatcher<Message>::~Dispatcher() {
    shutdown();
}

template<typename Message>
void StorageLinkQueued::Dispatcher<Message>::start()
{
    assert( ! _thread);
    _thread = _component->startThread(*this, 5s, 100ms);
}

template<typename Message>
void StorageLinkQueued::Dispatcher<Message>::add(const std::shared_ptr<Message>& m)
{
    std::unique_lock guard(_sync);

    if ( ! _thread) start();
    while ((_messages.size() > _maxQueueSize) && !_thread->interrupted()) {
        _syncCond.wait_for(guard, 100ms);
    }
    _messages.push_back(m);
    _syncCond.notify_one();
}

template<typename Message>
void StorageLinkQueued::Dispatcher<Message>::run(framework::ThreadHandle& h)
{
    while (!h.interrupted()) {
        h.registerTick(framework::PROCESS_CYCLE);
        std::shared_ptr<Message> message;
        {
            std::unique_lock guard(_sync);
            while (!h.interrupted() && _messages.empty()) {
                _syncCond.wait_for(guard, 100ms);
                h.registerTick(framework::WAIT_CYCLE);
            }
            if (h.interrupted()) break;
            message.swap(_messages.front());
        }
        try {
            send(message);
        } catch (std::exception& e) {
            _parent.logError(vespalib::make_string(
                    "When running command %s, caught exception %s. "
                    "Discarding message",
                    message->toString().c_str(),
                    e.what()).c_str());
        }

        {
            // Since flush() only waits for stack to be empty, we must
            // pop stack AFTER send have been called.
            std::lock_guard guard(_sync);
            _messages.pop_front();
            _syncCond.notify_one();
        }
    }
    _parent.logDebug("Finished storage link queued thread");
}

template<typename Message>
void StorageLinkQueued::Dispatcher<Message>::flush()
{
    using namespace std::chrono_literals;
    std::unique_lock guard(_sync);
    while (!_messages.empty()) {
        _syncCond.wait_for(guard, 100ms);
    }
}

}

