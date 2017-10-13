// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/persistence/spi/persistenceprovider.h>
#include <vespa/vespalib/util/exception.h>
#include <vespa/vespalib/util/sync.h>
#include <atomic>

namespace proton {

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
        virtual void send(ResultUP result, bool documentWasFound) = 0;
    };

    class State {
    public:
        State(const State &) = delete;
        State & operator = (const State &) = delete;
        State(ITransport & transport);
        ~State();
        void fail();
        void setResult(ResultUP result, bool documentWasFound) {
            _documentWasFound = documentWasFound;
            _result = std::move(result);
        }
        const storage::spi::Result &getResult() { return *_result; }
    private:
        void ack();
        ITransport           &_transport;
        ResultUP              _result;
        bool                  _documentWasFound;
        std::atomic<bool>     _alreadySent;
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
     */
    FeedToken(ITransport &transport);
    FeedToken();

    FeedToken(FeedToken &&) = default;
    FeedToken & operator =(FeedToken &&) = default;
    FeedToken(const FeedToken &) = default;
    FeedToken & operator =(const FeedToken &) = default;
    ~FeedToken() = default;

    explicit operator bool() const { return static_cast<bool>(_state); }
    State * operator ->() { return _state.get(); }
    const State * operator -> () const { return _state.get(); }
    void reset() { _state.reset(); }

    /**
     * Passes a receipt back to the originating FeedEngine, declaring that this
     * operation failed for some reason. Invoking this and/or fail() more than
     * once is void.
     *
     * @param errNum A numerical representation of the error.
     * @param errMsg A readable string detailing the error.
     */
    void fail() const { _state->fail(); }

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
};

} // namespace proton

