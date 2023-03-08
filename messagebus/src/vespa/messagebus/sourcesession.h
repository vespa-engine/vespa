// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "ireplyhandler.h"
#include "result.h"
#include "sequencer.h"
#include "sourcesessionparams.h"
#include "replygate.h"
#include <atomic>
#include <condition_variable>

namespace mbus {

class MessageBus;

/**
 * A SourceSession is used to send Message objects along a named or explicitly defined route and get Reply
 * objects back. A source session does not have a service name and can only receive replies to the messages
 * sent on it.
 **/
class SourceSession : public IReplyHandler {
private:
    friend class MessageBus;
    template <typename T> using ref_counted = vespalib::ref_counted<T>;

    std::mutex              _lock;
    std::condition_variable _cond;
    MessageBus             &_mbus;
    ref_counted<ReplyGate>  _gate;
    Sequencer               _sequencer;
    IReplyHandler          &_replyHandler;
    IThrottlePolicy::SP     _throttlePolicy;
    duration                _timeout;
    std::atomic<uint32_t>   _pendingCount;
    bool                    _closed;
    bool                    _done;

private:
    /**
     * This is the private constructor used by mbus to create source sessions. It expects all arguments but
     * the {@link SourceSessionParams} to be proper, so no checks are performed.
     *
     * @param mbus   The message bus that created this instance.
     * @param params A parameter object that holds configuration parameters.
     */
    SourceSession(MessageBus &mbus, const SourceSessionParams &params);

public:
    /**
     * Convenience typedef for an auto pointer to a SourceSession object.
     **/
    using UP = std::unique_ptr<SourceSession>;

    /**
     * The destructor untangles from messagebus. This is safe, but you will loose the replies of all pending
     * messages. After this method returns, messagebus will not invoke any handlers associated with this
     * session.
     **/
    ~SourceSession() override;

    /**
     * This is a convenience function to assign a named route to the given message, and then pass it to the
     * other {@link #send(Message)} method of this session. If the route could not be found this methods
     * returns with an appropriate error, unless the 'parseIfNotFound' argument is true. In that case, the
     * route name is passed through to the Route factory method {@link Route#create}.
     *
     * @param msg             The message to send.
     * @param routeName       The route to assign to the message.
     * @param parseIfNotFound Whether or not to parse routeName as a route if it could not be found.
     * @return The immediate result of the attempt to send this message.
     */
    Result send(Message::UP msg, const string &routeName, bool parseIfNotFound = false);

    /**
     * This is a convenience function to assign a given route to the given message, and then pass it to the
     * other {@link #send(Message)} method of this session.
     *
     * @param msg   The message to send.
     * @param route The route to assign to the message.
     * @return The immediate result of the attempt to send this message.
     */
    Result send(Message::UP msg, const Route &route);

    /**
     * Send a Message along a route that has already been specified in the message object.
     *
     * @return send result
     * @param msg the message to send
     */
    Result send(Message::UP msg);

    /**
     * Handle a Reply obtained from messagebus.
     *
     * @param reply the Reply
     **/
    void handleReply(Reply::UP reply) override;

    /**
     * Close this session. This method will block until Reply objects have been obtained for all pending
     * Message objects. Also, no more Message objects will be accepted by this session after closing has
     * initiated.
     **/
    void close();

    /**
     * Returns the reply handler of this session.
     *
     * @return The reply handler.
     */
    IReplyHandler &getReplyHandler() { return _replyHandler; }

    /**
     * Returns the number of messages sent that have not been replied to yet.
     *
     * @return The pending count.
     */
    [[nodiscard]] uint32_t getPendingCount() const noexcept {
        return _pendingCount.load(std::memory_order_relaxed);
    }

    /**
     * Sets the number of seconds a message can be attempted sent until it times out.
     *
     * @param timeout The numer of seconds allowed.
     * @return This, to allow chaining.
     */
    SourceSession &setTimeout(duration timeout);
};

} // namespace mbus

