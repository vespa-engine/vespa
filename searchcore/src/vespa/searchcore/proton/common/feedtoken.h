// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/util/idestructorcallback.h>
#include <atomic>

namespace storage::spi { class Result; }
namespace proton {

typedef std::unique_ptr<storage::spi::Result> ResultUP;

namespace feedtoken {

/**
 * This class is used by the FeedEngine to encapsulate the necessary information
 * for an IFeedHandler to perform an async reply to an operation. A unique
 * instance of this class is passed to every invocation of the IFeedHandler.
 */
class ITransport {
public:
    virtual ~ITransport() { }
    virtual void send(ResultUP result, bool documentWasFound) = 0;
};


/*
 * Interface class for feed token state.
 */
class IState : public vespalib::IDestructorCallback {
public:
    virtual bool is_replay() const noexcept = 0;
    virtual void fail() = 0;
    virtual void setResult(ResultUP result, bool documentWasFound) = 0;
    virtual const storage::spi::Result &getResult() = 0;
};


/**
 * This holds the result of the feed operation until it is either failed or acked.
 * Guarantees that the result is propagated back to the invoker via ITransport interface.
 */
class State : public IState {
public:
    State(const State &) = delete;
    State & operator = (const State &) = delete;
    State(ITransport & transport);
    ~State() override;
    bool is_replay() const noexcept override;
    void fail() override;
    void setResult(ResultUP result, bool documentWasFound) override;
    const storage::spi::Result &getResult() override { return *_result; }
protected:
    void ack();
private:
    ITransport           &_transport;
    ResultUP              _result;
    bool                  _documentWasFound;
    std::atomic<bool>     _alreadySent;
};

/**
 * This takes ownership ov the transport object, so that it can be used fully asynchronous
 * without invoker needing to hold any state.
 */
class OwningState : public State {
public:
    OwningState(std::shared_ptr<ITransport> transport)
        : State(*transport),
          _owned(std::move(transport))
    {}
    ~OwningState() override;
private:
    std::shared_ptr<ITransport> _owned;
};

inline std::shared_ptr<State>
make(ITransport & latch) {
    return std::make_shared<State>(latch);
}

inline std::shared_ptr<State>
make(std::shared_ptr<ITransport> transport) {
    return std::make_shared<OwningState>(std::move(transport));
}

}

using FeedToken = std::shared_ptr<feedtoken::IState>;

} // namespace proton

