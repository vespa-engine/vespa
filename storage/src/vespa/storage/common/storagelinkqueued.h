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
#include <sstream>
#include <memory>
#include <list>
#include <limits>
#include <vespa/storageframework/storageframework.h>
#include <vespa/storage/common/storagelink.h>

namespace storage {

class StorageLinkQueued : public StorageLink {
public:
    StorageLinkQueued(const std::string& name, framework::ComponentRegister& cr)
        : StorageLink(name),
          _compReg(cr),
          _replyDispatcher(*this),
          _commandDispatcher(*this),
          _closeState(0) {}
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
    virtual void onClose() {
        _commandDispatcher.flush();
        _closeState |= 1;
    }

    /** Remember to call this method if you override it. */
    virtual void onFlush(bool downwards) {
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
        void terminate() {
            if (_thread.get()) {
                _thread->interrupt();
                {
                    vespalib::MonitorGuard sync(_sync);
                    sync.signal();
                }
                _thread->join();
                _thread.reset(0);
            }
        }

    public:
        Dispatcher(StorageLinkQueued& parent, unsigned int maxQueueSize,
                   bool replyDispatcher)
            : _parent(parent),
              _maxQueueSize(maxQueueSize),
              _sync(),
              _messages(),
              _replyDispatcher(replyDispatcher)
        {
            std::ostringstream name;
            name << "Queued storage " << (_replyDispatcher ? "up" : "down")
                 << "link - " << _parent.getName();
            _component.reset(new framework::Component(
                    parent.getComponentRegister(),
                    name.str()));
        }

        virtual ~Dispatcher() {
            terminate();
        }

        void start();
        void run(framework::ThreadHandle&);

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
        void send(const std::shared_ptr<api::StorageMessage> & reply)
            { _parent.sendUp(reply); }
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
        void send(const std::shared_ptr<api::StorageMessage> & command)
            { _parent.sendDown(command); }
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

template<typename Message>
void StorageLinkQueued::Dispatcher<Message>::start()
{
    assert(_thread.get() == 0);
    framework::MilliSecTime maxProcessTime(5 * 1000);
    framework::MilliSecTime waitTime(100);
    _thread = _component->startThread(*this, maxProcessTime, waitTime);
}

template<typename Message>
void StorageLinkQueued::Dispatcher<Message>::add(
        const std::shared_ptr<Message>& m)
{
    vespalib::MonitorGuard sync(_sync);

    if (_thread.get() == 0) start();
    while ((_messages.size() > _maxQueueSize) && !_thread->interrupted()) {
        sync.wait(100);
    }
    _messages.push_back(m);
    sync.signal();
}

template<typename Message>
void StorageLinkQueued::Dispatcher<Message>::addWithoutLocking(
        const std::shared_ptr<Message>& m)
{
    if (_thread.get() == 0) start();
    _messages.push_back(m);
}

template<typename Message>
void StorageLinkQueued::Dispatcher<Message>::run(framework::ThreadHandle& h)
{
    while (!h.interrupted()) {
        h.registerTick(framework::PROCESS_CYCLE);
        std::shared_ptr<Message> message;
        {
            vespalib::MonitorGuard sync(_sync);
            while (!h.interrupted() && _messages.empty()) {
                sync.wait(100);
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
            vespalib::MonitorGuard sync(_sync);
            _messages.pop_front();
            sync.signal();
        }
    }
    _parent.logDebug("Finished storage link queued thread");
}

template<typename Message>
void StorageLinkQueued::Dispatcher<Message>::flush()
{
    vespalib::MonitorGuard sync(_sync);
    while (!_messages.empty()) {
        sync.wait(100);
    }
}

}

