// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * Storage link implementing a separate thread for dispatching replies.
 * Using this class you can use dispatchReply instead of sendReply to have the
 * replies sent through another thread.
 */

#pragma once

#include "storagelink.h"
#include <vespa/storageframework/generic/thread/runnable.h>
#include <condition_variable>
#include <deque>
#include <limits>
#include <mutex>

namespace storage {

namespace framework {
    struct ComponentRegister;
    class Component;
    class Thread;
}

class StorageLinkQueued : public StorageLink {
public:
    StorageLinkQueued(const std::string& name, framework::ComponentRegister& cr);
    ~StorageLinkQueued() override;

    /**
     * Add message to internal queue, to be dispatched downstream
     * in separate thread.
     */
    void dispatchUp(const std::shared_ptr<api::StorageMessage>&);

    /** Remember to call this method if you override it. */
    void onClose() override {
        _closeState |= 1;
    }

    /** Remember to call this method if you override it. */
    void onFlush(bool downwards) override {
        if (downwards) {
            _closeState |= 2;
        } else {
            _replyDispatcher.flush();
            _closeState |= 4;
        }
    }

    void logError(const char* error);
    void logDebug(const char* error);

    framework::ComponentRegister& getComponentRegister() { return _compReg; }

private:
    template<typename Message>
    class Dispatcher : public framework::Runnable
    {
    protected:
        StorageLinkQueued&                    _parent;
        unsigned int                          _maxQueueSize;
        std::mutex                            _sync;
        std::condition_variable               _syncCond;
        std::deque<std::shared_ptr<Message>>  _messages;
        bool                                  _replyDispatcher;
        std::unique_ptr<framework::Component> _component;
        std::unique_ptr<framework::Thread>    _thread;

        void shutdown();

    public:
        Dispatcher(StorageLinkQueued& parent, unsigned int maxQueueSize, bool replyDispatcher);

        ~Dispatcher() override;

        void start();
        void run(framework::ThreadHandle&) override;

        void add(const std::shared_ptr<Message>&);
        void flush();

        virtual void send(const std::shared_ptr<Message> & ) = 0;
    };

    class ReplyDispatcher : public Dispatcher<api::StorageMessage> {
    public:
        explicit ReplyDispatcher(StorageLinkQueued& parent)
            : Dispatcher<api::StorageMessage>(
                    parent, std::numeric_limits<unsigned int>::max(), true)
        {
        }
        void send(const std::shared_ptr<api::StorageMessage> & reply) override {
            _parent.sendUp(reply);
        }
    };

    framework::ComponentRegister& _compReg;
    ReplyDispatcher               _replyDispatcher;
    uint16_t                      _closeState;
};

}
