// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/messagebus/reply.h>
#include <vespa/persistence/spi/persistenceprovider.h>
#include <vespa/vespalib/util/exception.h>
#include <vespa/vespalib/util/sync.h>
#include <vespa/searchcore/proton/feedoperation/feedoperation.h>
#include <atomic>

namespace proton
{


class PerDocTypeFeedMetrics;
typedef std::unique_ptr<storage::spi::Result> ResultUP;

/**
 * This class is used by the FeedEngine to encapsulate the necessary information
 * for an IFeedHandler to perform an async reply to an operation. A unique
 * instance of this class is passed to every invokation of the IFeedHandler.
 */
class FeedToken {
public:
    class ITransport {
    public:
        virtual ~ITransport() { }
        virtual void send(mbus::Reply::UP reply,
                          ResultUP result,
                          bool documentWasFound,
                          double latency_ms) = 0;
    };

private:
    class State {
    public:
        State(const State &) = delete;
        State & operator = (const State &) = delete;
        State(ITransport & transport, mbus::Reply::UP reply, uint32_t numAcksRequired);
        ~State();
        void setNumAcksRequired(uint32_t numAcksRequired) { _unAckedCount = numAcksRequired; }
        void ack();

        void ack(const FeedOperation::Type opType, PerDocTypeFeedMetrics &metrics);

        void incNeededAcks();

        void fail(uint32_t errNum, const vespalib::string &errMsg);
        void trace(uint32_t traceLevel, const vespalib::string &traceMsg);
        bool shouldTrace(uint32_t traceLevel) const { return _reply->getTrace().shouldTrace(traceLevel); }
        mbus::Reply & getReply() { return *_reply; }
        void setResult(ResultUP result, bool documentWasFound) {
            _documentWasFound = documentWasFound;
            _result = std::move(result);
        }
        const storage::spi::Result &getResult() { return *_result; }
        FastOS_Time getStartTime() const { return _startTime; }
    private:
        ITransport       &_transport;
        mbus::Reply::UP   _reply;
        ResultUP          _result;
        bool              _documentWasFound;
        std::atomic<uint32_t> _unAckedCount;
        vespalib::Lock    _lock;
        FastOS_Time       _startTime;
    };
    std::shared_ptr<State> _state;

public:
    typedef std::unique_ptr<FeedToken> UP;
    typedef std::shared_ptr<FeedToken> SP;

    /**
     * Constructs a unique FeedToken. This is the constructor used by the
     * FeedEngine when processing input. If the given message is empty, or it
     * does not belong to the document protocol, this method throws a
     * vespalib::IllegalArgumentException.
     *
     * @param transport The transport to pass the reply to.
     * @param reply     The mbus::Reply corresponding to this operation.
     */
    FeedToken(ITransport &transport, mbus::Reply::UP reply);

    /**
     * Passes a receipt back to the originating FeedEngine, declaring that this
     * operation succeeded. If an error occured while processing the operation,
     * use fail() instead. Invoking this and/or fail() more than once is void.
     */
    void ack() const { _state->ack(); }

    void
    ack(const FeedOperation::Type opType, PerDocTypeFeedMetrics &metrics) const
    {
        _state->ack(opType, metrics);
    }

    void
    incNeededAcks() const
    {
        _state->incNeededAcks();
    }

    /**
     * Passes a receipt back to the originating FeedEngine, declaring that this
     * operation failed for some reason. Invoking this and/or fail() more than
     * once is void.
     *
     * @param errNum A numerical representation of the error.
     * @param errMsg A readable string detailing the error.
     */
    void fail(uint32_t errNum, const vespalib::string &errMsg) const { _state->fail(errNum, errMsg); }

    /**
     * Writes a trace message to the receipt of this operation that will later
     * be passed back to the FeedEngine through ack() or fail().
     *
     * @param traceLevel The level of the message to write.
     * @param traceMsg   The message to write.
     */
    void trace(uint32_t traceLevel, const vespalib::string &traceMsg) const { _state->trace(traceLevel, traceMsg); }

    /**
     * Tell you if tracing at this level is enabled
     *
     * @param traceLevel The level you want to trace at.
     * @return if you should trace or not.
     */
    bool shouldTrace(uint32_t traceLevel) const { return _state->shouldTrace(traceLevel); }

    /**
     * Gives you access to the underlying reply message.
     *
     * @return The reply
     */
    mbus::Reply & getReply() const { return _state->getReply(); }

    /**
     * Gives you access to the underlying result.
     *
     * @return The result
     */
    const storage::spi::Result &getResult() const { return _state->getResult(); }

    /**
     * Sets the underlying result.
     */
    void setResult(ResultUP result, bool documentWasFound) {
        _state->setResult(std::move(result), documentWasFound);
    }

    /**
     * This controls how many acks are required before it is acked back to the sender.
     * Default is 1, and so far only adjusted by multioperation handling.
     *
     * @param numAcksRequired How many acks must be received before it is considered acked.
     */
    void setNumAcksRequired(uint32_t numAcksRequired) const { _state->setNumAcksRequired(numAcksRequired); }

    FastOS_Time getStartTime() const { return _state->getStartTime(); }
};

} // namespace proton

