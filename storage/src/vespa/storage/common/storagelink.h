// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @class storage::StorageLink
 * @ingroup common
 *
 * @brief Base class for StorageServer modules.
 *
 * Base class for StorageServer modules. Each module receives commands from
 * "upstream" and replies from "downstream". It can choose to intercept both
 * these streams via the onDown and onUp methods. The base class methods
 * calls the hooks from MessageHandler. The handlers should return true if the
 * message has been handled and should not be sent to the next module.
 *
 * Replies to messages should not be dispatched from within onDown. Create a
 * separate thread and dispatch messages from this (or use StorageChainQueued).
 *
 * @version $Id$
 */

#pragma once

#include "messagesender.h"
#include <vespa/storageapi/messageapi/messagehandler.h>
#include <vespa/storageapi/messageapi/storagemessage.h>
#include <vespa/document/util/printable.h>
#include <atomic>
#include <queue>
#include <mutex>
#include <condition_variable>

namespace storage {

struct FileStorManagerTest;

class StorageLink : public document::Printable,
                    public ChainedMessageSender,
                    protected api::MessageHandler
{
public:
    using UP = std::unique_ptr<StorageLink>;

    enum State { CREATED, OPENED, CLOSING, FLUSHINGDOWN, FLUSHINGUP, CLOSED };

    enum class MsgDownOnFlush { Allowed, Disallowed };
    enum class MsgUpOnClosed  { Allowed, Disallowed };

private:
    const std::string            _name;
    StorageLink*                 _up;
    std::unique_ptr<StorageLink> _down;
    std::atomic<State>           _state;
    const MsgDownOnFlush         _msg_down_during_flushing;
    const MsgUpOnClosed          _msg_up_during_closed;

public:
    StorageLink(const std::string& name,
                MsgDownOnFlush allow_msg_down_during_flushing,
                MsgUpOnClosed allow_msg_up_during_closed);
    explicit StorageLink(const std::string& name);

    StorageLink(const StorageLink &) = delete;
    StorageLink & operator = (const StorageLink &) = delete;
    ~StorageLink() override;

    const std::string& getName() const noexcept { return _name; }
    [[nodiscard]] bool isTop() const noexcept { return !_up; }
    [[nodiscard]] bool isBottom() const noexcept { return !_down; }
    [[nodiscard]] unsigned int size() const noexcept {
        return (isBottom() ? 1 : _down->size() + 1);
    }

    /** Adds the link to the end of the chain. */
    void push_back(StorageLink::UP);

    /** Get the current state of the storage link. */
    [[nodiscard]] State getState() const noexcept {
        return _state.load(std::memory_order_relaxed);
    }

    /**
     * Called by storage server after the storage chain have been created.
     */
    void open();

    void doneInit();

    /**
     * Mark this link as closed. After close is called, the link should not
     * accept requests from external sources. (Internal sources still ok)
     */
    void close();

    /**
     * Flush messages through this link. Allways called after close() and
     * before deletion, to remove any queued up messages.
     */
    void flush();

    /** Send message down the storage chain. */
    void sendDown(const api::StorageMessage::SP&) override;

    /** Send message up the storage chain. */
    void sendUp(const api::StorageMessage::SP&) override;

    void printChain(std::ostream&, std::string indent = "") const;

    /** Used for debugging/testing. */
    StorageLink* getNextLink() { return _down.get(); }
    void addTestLinkOnTop(StorageLink* up) { _up = up; }

    virtual void storageDistributionChanged() {}

    /**
     * Called for each command message. Default implementation calls hooks
     * from MessageHandler. Either overload this or the MessageHandler
     * hooks to implement the module. In most cases, if you return true,
     * you should create and dispatch a reply message.
     *
     * This function should only be called by storage links sendDown, or
     * from storage links implementing it to default to default behavior.
     *
     * @return True if message is handled, false if it should be passed
     *         to the next module.
     */
    virtual bool onDown(const api::StorageMessage::SP&);

    /**
     * Called for each reply message. Default implementation calls hooks
     * from MessageHandler. Either overload this or the MessageHandler
     * hooks to implement the module. If you intercept and return true for
     * a reply, it should either be a reply to a command your module sent,
     * or you should construct a new reply message and dispatch that.
     *
     * This function should only be called by storage links sendUp, or
     * from storage links implementing it to default to default behavior.
     *
     * @return True if message is handled, false if it should be passed
     *         to the next module.
     */
    virtual bool onUp(const api::StorageMessage::SP&);

    void print(std::ostream& out, bool, const std::string&) const override;

    static const char* stateToString(State state);

protected:
    /**
     * To ensure that the storage chain is deleted bottom-up, each storage
     * link must call closeNextLink first in it's destructor, such that all
     * links below are deleted before it deletes itself.
     *
     * This function should only be called from storage link destructor.
     */
    void closeNextLink();

private:
    /**
     * Called from open(), after all links in the chain have been set up and
     * initialized. In onOpen() and after, links are allowed to send messages
     * both up and down. (Though should likely only send down)
     */
    virtual void onOpen() {}

    /**
     * Called from doneInit(), after node is done initializing.
     */
    virtual void onDoneInit() {}

    /**
     * Called from close. Override if you need to react to close calls.
     * After close, no new operations can be requested. RPC servers should no
     * longer accept incoming messages, web server taking HTTP requests should
     * be shut down or no longer accept requests, background task schedulers
     * should no longer schedule tasks, etc.
     */
    virtual void onClose() {}

    /**
     * Called from flush. Override if your class contains anything flushable.
     * Flush is called twice after onClose() (and never at any other time).
     * First time it is called on the way down the storage chain. Second time
     * it is called on the way up the storage chain. On the way down, link
     * must flush all operations scheduled to be sent down. Since chain is
     * closed while this is happening. No new requests should happen until
     * flush is called upwards. At that time links must flush all messages going
     * upwards the chain. After this has been done, no messages/operations
     * should remain in the process.
     */
    virtual void onFlush(bool downwards) { (void) downwards; }

    /**
     * Some unit tests wants access to private functions. They can do this
     * through the storage link test.
     */
    friend struct StorageLinkTest;
};

class Queue {
private:
    using QueueType = std::queue<std::shared_ptr<api::StorageMessage>>;
    QueueType               _queue;
    mutable std::mutex      _lock;
    std::condition_variable _cond;

public:
    Queue();
    ~Queue();

    /**
     * Returns the next event from the event queue
     * @param   msg             The next event
     * @param   timeout         Millisecs to wait if the queue is empty
     * @return  true or false if the queue was empty.
     */
    bool getNext(std::shared_ptr<api::StorageMessage>& msg, vespalib::duration timeout);

    /**
     * Enqueue msg in FIFO order.
     */
    void enqueue(std::shared_ptr<api::StorageMessage> msg);

    /** Signal queue monitor. */
    void signal();

    size_t size() const;
};

std::ostream& operator<<(std::ostream& out, StorageLink& link);

}
