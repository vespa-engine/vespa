// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "imessagehandler.h"
#include "ireplyhandler.h"
#include "message.h"
#include "reply.h"
#include "queue.h"
#include <mutex>
#include <map>

namespace mbus {

/**
 * A Sequencer ensures correct sequencing of pending messages. When a Sequencer is created, it is given an
 * object implementing the IMessageHandler API to use for sending messages. This class is used by the
 * SourceSession class and is not intended for external use.
 */
class Sequencer : public IMessageHandler,
                  public IReplyHandler
{
private:
    std::mutex      _lock;
    IMessageHandler &_sender;

    using MessageQueue = Queue<Message*>;
    using QueueMap = std::map<uint64_t, MessageQueue*>;
    QueueMap _seqMap;

private:
    /**
     * Filter a message against the current sequencing state. If the message is returned back out again, it
     * has been cleared for sending and its sequencing information has been added to the state. If the message
     * is not returned it has been queued for later sending due to sequencing restrictions. This method also
     * sets the sequence id as message context.
     *
     * @param msg The message to filter.
     * @return The argument message if it passed the filter.
     */
    Message::UP filter(Message::UP msg);

    /**
     * Internal method for forwarding a sequenced message to the underlying sender.
     *
     * @param msg The message to forward.
     */
    void sequencedSend(Message::UP msg);

public:
    /**
     * Convenience typedef for an auto pointer to a Sequencer object.
     */
    using UP = std::unique_ptr<Sequencer>;

    /**
     * Create a new Sequencer using the given sender to send messages.
     *
     * @param sender The underlying sender.
     */
    Sequencer(IMessageHandler &sender);

    /**
     * Destruct. This will also destruct any Message objects held back due to sequencing collisions.
     */
    virtual ~Sequencer();

    /**
     * All messages pass through this handler when being sent by the owning source session. In case the
     * message has no sequencing-id, it is simply passed through to the next handler in the chain. Sequenced
     * messages are sent only if there is no queue for their id, otherwise they are queued.
     *
     * @param msg The message to send.
     */
    void handleMessage(Message::UP msg) override;

    /**
     * Lookup the sequencing id of an incoming reply to pop the front of the corresponding queue, and then
     * send the next message in line, if any.
     *
     * @param reply The reply received.
     */
    void handleReply(Reply::UP reply) override;
};

} // namespace mbus

