// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "idiscardhandler.h"
#include "imessagehandler.h"
#include "ireplyhandler.h"
#include <vespa/vespalib/util/ref_counted.h>
#include <atomic>

namespace mbus {

/**
 * A ReplyGate will forward replies until it is closed. After being closed, the
 * gate will silently delete all replies. The ReplyGate class has external
 * reference counting. This class is used by session objects to perform safe
 * untangling from messagebus when being destructed while having pending
 * messages. The reference counting is needed to ensure that the object is alive
 * until all pending replies have been correctly ignored. Thread synchronization
 * is handled outside this class. Note that this class is only intended for
 * internal use.
 */
class ReplyGate : public vespalib::enable_ref_counted,
                  public IDiscardHandler,
                  public IMessageHandler,
                  public IReplyHandler
{
private:
    IMessageHandler &_sender;
    std::atomic<bool> _open;

public:
    /**
     * Create a new ReplyGate.
     *
     * @param sender The underlying IMessageHandler object.
     */
    ReplyGate(IMessageHandler &sender);

    /**
     * Send a Message to the underlying IMessageHandler. This method will
     * increase the reference counter to ensure that this object is alive until
     * the matching Reply has been obtained. In order to obtain the matching
     * Reply, this method will push this object on the CallStack of the Message.
     */
    void handleMessage(std::unique_ptr<Message> msg) override;

    /**
     * Forward or discard Reply. If the gate is still open, it will forward the
     * Reply to the next IReplyHandler on the CallStack. If the gate is closed,
     * the Reply will be discarded. This method also decreases the reference
     * counter of this object.
     */
    void handleReply(std::unique_ptr<Reply> reply) override;

    // Implements IDiscardHandler.
    void handleDiscard(Context ctx) override;

    /**
     * Close this gate. After this has been invoked, the gate will start to
     * discard Reply objects. A closed gate can never be re-opened.
     */
    void close();
};

} // namespace mbus
