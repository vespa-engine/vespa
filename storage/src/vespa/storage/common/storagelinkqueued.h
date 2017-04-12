// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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

#include <vespa/vespalib/util/document_runnable.h>
#include <deque>
#include <limits>
#include <vespa/storageframework/storageframework.h>
#include <vespa/storage/common/storagelink.h>

namespace storage {

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
    virtual void onClose() override {
        _commandDispatcher.flush();
        _closeState |= 1;
    }

    /** Remember to call this method if you override it. */
    virtual void onFlush(bool downwards) override {
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
        vespalib::Monitor _sync;
        std::deque< std::shared_ptr<Message> > _messages;
        bool _replyDispatcher;
        framework::Component::UP _component;
        framework::Thread::UP _thread;
        void terminate();

    public:
        Dispatcher(StorageLinkQueued& parent, unsigned int maxQueueSize, bool replyDispatcher);

        virtual ~Dispatcher();

        void start();
        void run(framework::ThreadHandle&) override;

        void add(const std::shared_ptr<Message>&);
        void flush();
            // You can use the given functions if you need to keep the
            // dispatcher thread locked while you process a message. Bucket
            // manager does this during bucket dumps
        vespalib::Monitor& getMonitor() { return _sync; }
        void addWithoutLocking(const std::shared_ptr<Message>&);

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
        virtual ~ReplyDispatcher() { terminate(); }
    };

    class CommandDispatcher : public Dispatcher<api::StorageMessage>
    {
    public:
        CommandDispatcher(StorageLinkQueued& parent)
            : Dispatcher<api::StorageMessage>(
                    parent, std::numeric_limits<unsigned int>::max(), false)
        {
        }
        virtual ~CommandDispatcher() { terminate(); }
        void send(const std::shared_ptr<api::StorageMessage> & command) override {
            _parent.sendDown(command);
        }
    };

    framework::ComponentRegister& _compReg;
    framework::Thread::UP _replyThread;
    framework::Thread::UP _commandThread;
    ReplyDispatcher    _replyDispatcher;
    CommandDispatcher  _commandDispatcher;
    uint16_t _closeState;

protected:
    ReplyDispatcher& getReplyDispatcher() { return _replyDispatcher; }

};

}
