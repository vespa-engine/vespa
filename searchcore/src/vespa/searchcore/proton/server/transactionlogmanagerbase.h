// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/transactionlog/translogclient.h>
#include <mutex>
#include <condition_variable>
#include <vespa/fastos/timestamp.h>

namespace proton {

/**
 * Base class managing the initialization and replay of a transaction log.
 **/
class TransactionLogManagerBase {
protected:
    using TransLogClient = search::transactionlog::TransLogClient;
private:
    TransLogClient                  _tlc;
    TransLogClient::Session::UP     _tlcSession;
    vespalib::string                _domainName;
    mutable std::mutex              _replayLock;
    mutable std::condition_variable _replayCond;
    volatile bool                   _replayDone;
    bool                            _replayStarted;
    fastos::StopWatch               _replayStopWatch;

protected:
    typedef search::SerialNum SerialNum;

    struct StatusResult {
        SerialNum serialBegin;
        SerialNum serialEnd;
        size_t    count;
        StatusResult() : serialBegin(0), serialEnd(0), count(0) {}
    };

    StatusResult init();

    void internalStartReplay();
    virtual void doLogReplayComplete(const vespalib::string &domainName, std::chrono::milliseconds elapsedTime) const = 0;

public:
    TransactionLogManagerBase(const TransactionLogManagerBase &) = delete;
    TransactionLogManagerBase & operator = (const TransactionLogManagerBase &) = delete;
    /**
     * Create a new manager.
     *
     * @param tlsSpec the spec of the transaction log server.
     * @param domainName the name of the domain this manager should handle.
     **/
    TransactionLogManagerBase(const vespalib::string &tlsSpec,
                              const vespalib::string &domainName);
    virtual ~TransactionLogManagerBase();

    void changeReplayDone();
    void close();
    TransLogClient::Visitor::UP createTlcVisitor(TransLogClient::Session::Callback &callback);

    void waitForReplayDone() const;

    TransLogClient &getClient() { return _tlc; }
    TransLogClient::Session *getSession() { return _tlcSession.get(); }
    const vespalib::string &getDomainName() const { return _domainName; }
    bool getReplayDone() const;
    bool isDoingReplay() const;
    void logReplayComplete() const;
    const vespalib::string &getRpcTarget() const { return _tlc.getRPCTarget(); }
};

} // namespace proton
