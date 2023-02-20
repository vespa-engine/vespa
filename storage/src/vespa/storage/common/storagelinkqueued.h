// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @class storage::StorageLinkQueued
 * @ingroup common
 *
 * @brief Storage link with a message queue.
 *
 * Storage link implementing separate threads for dispatching messages.
 * Using this class you can use dispatchReply instead of sendReply to have the
 * replies sent through another thread.
 *
 * @version $Id$
 */

#pragma once

#include "storagelink.h"
#include <vespa/storageframework/generic/thread/runnable.h>
#include <deque>
#include <limits>
#include <mutex>
#include <condition_variable>

namespace storage {

namespace framework {
    struct ComponentRegister;
    class Component;
    class Thread;
}

class StorageLinkQueued : public StorageLink {
public:
    StorageLinkQueued(const std::string& name, framework::ComponentRegister& cr);
    virtual ~StorageLinkQueued();

    /**
     * Add message to internal queue, to be dispatched downstream
     * in separate thread.
     */
    void dispatchDown(const std::shared_ptr<api::StorageMessage>&);

    /**
     * Add message to internal queue, to be dispatched downstream
     * in separate thread.
     */
    void dispatchUp(const std::shared_ptr<api::StorageMessage>&);

    /** Remember to call this method if you override it. */
    void onClose() override {
        _commandDispatcher.flush();
        _closeState |= 1;
    }

    /** Remember to call this method if you override it. */
    void onFlush(bool downwards) override {
        if (downwards) {
            _commandDispatcher.flush();
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
    /** Common class to prevent need for duplicate code. */
    template<typename Message>
    class Dispatcher : public framework::Runnable
    {
    protected:
        StorageLinkQueued& _parent;
        unsigned int _maxQueueSize;
        std::mutex              _sync;
        std::condition_variable _syncCond;
        std::deque< std::shared_ptr<Message> > _messages;
        bool _replyDispatcher;
        std::unique_ptr<framework::Component> _component;
        std::unique_ptr<framework::Thread> _thread;
        void terminate();

    public:
        Dispatcher(StorageLinkQueued& parent, unsigned int maxQueueSize, bool replyDispatcher);

        ~Dispatcher();

        void start();
        void run(framework::ThreadHandle&) override;

        void add(const std::shared_ptr<Message>&);
        void flush();

        virtual void send(const std::shared_ptr<Message> & ) = 0;
    };

    class ReplyDispatcher : public Dispatcher<api::StorageMessage>
    {
    public:
        ReplyDispatcher(StorageLinkQueued& parent)
            : Dispatcher<api::StorageMessage>(
                    parent, std::numeric_limits<unsigned int>::max(), true)
        {
        }
        void send(const std::shared_ptr<api::StorageMessage> & reply) override {
            _parent.sendUp(reply);
        }
        ~ReplyDispatcher() { terminate(); }
    };

    class CommandDispatcher : public Dispatcher<api::StorageMessage>
    {
    public:
        CommandDispatcher(StorageLinkQueued& parent)
            : Dispatcher<api::StorageMessage>(
                    parent, std::numeric_limits<unsigned int>::max(), false)
        {
        }
         ~CommandDispatcher() { terminate(); }
        void send(const std::shared_ptr<api::StorageMessage> & command) override {
            _parent.sendDown(command);
        }
    };

    framework::ComponentRegister& _compReg;
    ReplyDispatcher    _replyDispatcher;
    CommandDispatcher  _commandDispatcher;
    uint16_t _closeState;

protected:
    ReplyDispatcher& getReplyDispatcher() { return _replyDispatcher; }
};

}
