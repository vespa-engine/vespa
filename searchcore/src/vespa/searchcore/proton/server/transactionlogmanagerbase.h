// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/transactionlog/translogclient.h>

namespace proton {

/**
 * Base class managing the initialization and replay of a transaction log.
 **/
class TransactionLogManagerBase {

    search::transactionlog::TransLogClient _tlc;
    search::transactionlog::TransLogClient::Session::UP _tlcSession;
    vespalib::string            _domainName;
    vespalib::Monitor           _replayMonitor;
    volatile bool               _replayDone;
    bool                        _replayStarted;
    double                      _replayStartTime;

protected:
    typedef search::transactionlog::TransLogClient TransLogClient;
    typedef search::SerialNum SerialNum;

    struct StatusResult {
        SerialNum serialBegin;
        SerialNum serialEnd;
        size_t    count;
        StatusResult() : serialBegin(0), serialEnd(0), count(0) {}
    };

    StatusResult init();

    void internalStartReplay();
    virtual void doLogReplayComplete(const vespalib::string &domainName,
                                     int64_t elapsedTime) const = 0;

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
    TransLogClient::Subscriber::UP createTlcSubscriber(
            TransLogClient::Session::Callback &callback);
    TransLogClient::Visitor::UP createTlcVisitor(
            TransLogClient::Session::Callback &callback);

    void waitForReplayDone() const;

    TransLogClient &getClient() { return _tlc; }
    TransLogClient::Session *getSession() { return _tlcSession.get(); }
    const vespalib::string &getDomainName() const { return _domainName; }
    bool getReplayDone() const;
    bool isDoingReplay() const;
    void logReplayComplete() const;
    const vespalib::string &getRpcTarget() const
    { return _tlc.getRPCTarget(); }

    void
    markReplayStarted();
};

} // namespace proton

