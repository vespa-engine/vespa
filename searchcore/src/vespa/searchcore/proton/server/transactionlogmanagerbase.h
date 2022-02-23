// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/common/serialnum.h>
#include <vespa/vespalib/util/time.h>
#include <mutex>
#include <condition_variable>

class FNET_Transport;

namespace search::transactionlog::client {
    class TransLogClient;
    class Session;
    class Visitor;
    class Callback;
}
namespace proton {

/**
 * Base class managing the initialization and replay of a transaction log.
 **/
class TransactionLogManagerBase {
protected:
    using TransLogClient = search::transactionlog::client::TransLogClient;
    using Session = search::transactionlog::client::Session;
    using Visitor = search::transactionlog::client::Visitor;
    using Callback = search::transactionlog::client::Callback;
private:
    std::unique_ptr<TransLogClient> _tlc;
    std::unique_ptr<Session>        _tlcSession;
    vespalib::string                _domainName;
    mutable std::mutex              _replayLock;
    mutable std::condition_variable _replayCond;
    volatile bool                   _replayDone;
    bool                            _replayStarted;
    vespalib::Timer                 _replayStopWatch;

protected:
    using SerialNum = search::SerialNum;

    struct StatusResult {
        SerialNum serialBegin;
        SerialNum serialEnd;
        size_t    count;
        StatusResult() : serialBegin(0), serialEnd(0), count(0) {}
    };

    StatusResult init();

    void internalStartReplay();
    virtual void doLogReplayComplete(const vespalib::string &domainName, vespalib::duration elapsedTime) const = 0;

public:
    TransactionLogManagerBase(const TransactionLogManagerBase &) = delete;
    TransactionLogManagerBase & operator = (const TransactionLogManagerBase &) = delete;
    /**
     * Create a new manager.
     *
     * @param tlsSpec the spec of the transaction log server.
     * @param domainName the name of the domain this manager should handle.
     **/
    TransactionLogManagerBase(FNET_Transport & transport,
                              const vespalib::string &tlsSpec,
                              const vespalib::string &domainName);
    virtual ~TransactionLogManagerBase();

    void changeReplayDone();
    void close();
    std::unique_ptr<Visitor> createTlcVisitor(Callback &callback);

    void waitForReplayDone() const;

    TransLogClient &getClient() { return *_tlc; }
    Session *getSession() { return _tlcSession.get(); }
    const vespalib::string &getDomainName() const { return _domainName; }
    bool getReplayDone() const;
    bool isDoingReplay() const;
    void logReplayComplete() const;
    const vespalib::string &getRpcTarget() const;
};

} // namespace proton
