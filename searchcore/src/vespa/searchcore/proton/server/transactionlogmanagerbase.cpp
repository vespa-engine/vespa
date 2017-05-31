// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "transactionlogmanagerbase.h"
#include <vespa/vespalib/util/stringfmt.h>

#include <vespa/log/log.h>
LOG_SETUP(".proton.server.transactionlogmanagerbase");

using search::transactionlog::TransLogClient;

namespace proton {


TransactionLogManagerBase::TransactionLogManagerBase(
        const vespalib::string &tlsSpec, const vespalib::string &domainName) :
    _tlc(tlsSpec),
    _tlcSession(),
    _domainName(domainName),
    _replayMonitor(),
    _replayDone(false),
    _replayStarted(false),
    _replayStartTime(0)
{
}

TransactionLogManagerBase::~TransactionLogManagerBase()
{
}


TransactionLogManagerBase::StatusResult
TransactionLogManagerBase::init()
{
    TransLogClient::Session::UP session = _tlc.open(_domainName);
    if (session.get() == NULL) {
        if (!_tlc.create(_domainName)) {
            vespalib::string str = vespalib::make_string(
                    "Failed creating domain '%s' on TLS '%s'",
                    _domainName.c_str(), _tlc.getRPCTarget().c_str());
            throw std::runtime_error(str);
        }
        LOG(debug, "Created domain '%s' on TLS '%s'",
            _domainName.c_str(), _tlc.getRPCTarget().c_str());
        session = _tlc.open(_domainName);
        if (session.get() == NULL) {
            vespalib::string str = vespalib::make_string(
                    "Could not open session for domain '%s' on TLS '%s'",
                    _domainName.c_str(), _tlc.getRPCTarget().c_str());
            throw std::runtime_error(str);
        }
    }
    LOG(debug, "Opened domain '%s' on TLS '%s'",
        _domainName.c_str(), _tlc.getRPCTarget().c_str());
    StatusResult res;
    if (!session->status(res.serialBegin, res.serialEnd, res.count)) {
        vespalib::string str = vespalib::make_string(
                "Could not get status from session with domain '%s' on TLS '%s'",
                _domainName.c_str(), _tlc.getRPCTarget().c_str());
        throw std::runtime_error(str);
    }
    LOG(debug,
        "Status for domain '%s': serialBegin(%" PRIu64 "), serialEnd(%" PRIu64 "), count(%zu)",
        _domainName.c_str(), res.serialBegin, res.serialEnd, res.count);
    _tlcSession = std::move(session);
    return res;
}


void
TransactionLogManagerBase::internalStartReplay()
{
    vespalib::MonitorGuard guard(_replayMonitor);
    _replayStarted = true;
    _replayDone = false;
    FastOS_Time timer;
    timer.SetNow();
    _replayStartTime = timer.MilliSecs();
}


void
TransactionLogManagerBase::markReplayStarted()
{
    vespalib::MonitorGuard guard(_replayMonitor);
    _replayStarted = true;
}


void TransactionLogManagerBase::changeReplayDone()
{
    vespalib::MonitorGuard guard(_replayMonitor);
    _replayDone = true;
    guard.broadcast();
}


void
TransactionLogManagerBase::waitForReplayDone() const
{
    vespalib::MonitorGuard guard(_replayMonitor);
    while (_replayStarted && !_replayDone) {
        guard.wait();
    }
}


void
TransactionLogManagerBase::close()
{
    if (_tlcSession.get() != NULL) {
        _tlcSession->close();
    }
    // Delay destruction until replay is not active.
    waitForReplayDone();
    if (_tlcSession.get() != NULL) {
        _tlcSession->clear();
    }
}

TransLogClient::Subscriber::UP TransactionLogManagerBase::createTlcSubscriber(
        TransLogClient::Session::Callback &callback) {
    return _tlc.createSubscriber(_domainName, callback);
}

TransLogClient::Visitor::UP TransactionLogManagerBase::createTlcVisitor(
        TransLogClient::Session::Callback &callback) {
    return _tlc.createVisitor(_domainName, callback);
}

bool TransactionLogManagerBase::getReplayDone() const {
    vespalib::MonitorGuard guard(_replayMonitor);
    return _replayDone;
}

bool TransactionLogManagerBase::isDoingReplay() const {
    vespalib::MonitorGuard guard(_replayMonitor);
    return _replayStarted && !_replayDone;
}

void TransactionLogManagerBase::logReplayComplete() const {
    FastOS_Time timer;
    timer.SetMilliSecs(_replayStartTime);
    doLogReplayComplete(_domainName, static_cast<int64_t>(timer.MilliSecsToNow()));
}

} // namespace proton
