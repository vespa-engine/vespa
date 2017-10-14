// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/persistence/spi/persistenceprovider.h>
#include <vespa/searchlib/common/idestructorcallback.h>
#include <vespa/vespalib/util/sync.h>
#include <atomic>

namespace proton {

typedef std::unique_ptr<storage::spi::Result> ResultUP;

/**
 * This class is used by the FeedEngine to encapsulate the necessary information
 * for an IFeedHandler to perform an async reply to an operation. A unique
 * instance of this class is passed to every invokation of the IFeedHandler.
 */
namespace feedtoken {
    class ITransport {
    public:
        virtual ~ITransport() { }
        virtual void send(ResultUP result, bool documentWasFound) = 0;
    };

    class State : public search::IDestructorCallback {
    public:
        State(const State &) = delete;
        State & operator = (const State &) = delete;
        State(ITransport & transport);
        ~State() override;
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

    inline std::shared_ptr<State>
    make(ITransport & latch) {
        return std::make_shared<State>(latch);
    }
}

using FeedToken = std::shared_ptr<feedtoken::State>;

} // namespace proton

