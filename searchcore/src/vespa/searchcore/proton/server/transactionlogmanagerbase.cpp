// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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
    _replayLock(),
    _replayCond(),
    _replayDone(false),
    _replayStarted(false),
    _replayStopWatch()
{
}

TransactionLogManagerBase::~TransactionLogManagerBase() = default;

TransactionLogManagerBase::StatusResult
TransactionLogManagerBase::init()
{
    TransLogClient::Session::UP session = _tlc.open(_domainName);
    if ( ! session) {
        if (!_tlc.create(_domainName)) {
            vespalib::string str = vespalib::make_string(
                    "Failed creating domain '%s' on TLS '%s'",
                    _domainName.c_str(), _tlc.getRPCTarget().c_str());
            throw std::runtime_error(str);
        }
        LOG(debug, "Created domain '%s' on TLS '%s'",
            _domainName.c_str(), _tlc.getRPCTarget().c_str());
        session = _tlc.open(_domainName);
        if ( ! session) {
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
    std::lock_guard<std::mutex> guard(_replayLock);
    _replayStarted = true;
    _replayDone = false;
    _replayStopWatch.restart();
}

void TransactionLogManagerBase::changeReplayDone()
{
    std::lock_guard<std::mutex> guard(_replayLock);
    _replayDone = true;
    _replayCond.notify_all();
}

void
TransactionLogManagerBase::waitForReplayDone() const
{
    std::unique_lock<std::mutex> guard(_replayLock);
    while (_replayStarted && !_replayDone) {
        _replayCond.wait(guard);
    }
}

void
TransactionLogManagerBase::close()
{
    if (_tlcSession) {
        _tlcSession->close();
    }
    // Delay destruction until replay is not active.
    waitForReplayDone();
    if (_tlcSession) {
        _tlcSession->clear();
    }
}

TransLogClient::Visitor::UP
TransactionLogManagerBase::createTlcVisitor(TransLogClient::Session::Callback &callback) {
    return _tlc.createVisitor(_domainName, callback);
}

bool TransactionLogManagerBase::getReplayDone() const {
    std::lock_guard<std::mutex> guard(_replayLock);
    return _replayDone;
}

bool TransactionLogManagerBase::isDoingReplay() const {
    std::lock_guard<std::mutex> guard(_replayLock);
    return _replayStarted && !_replayDone;
}

void TransactionLogManagerBase::logReplayComplete() const {
    doLogReplayComplete(_domainName, std::chrono::milliseconds(_replayStopWatch.elapsed().ms()));
}

} // namespace proton
