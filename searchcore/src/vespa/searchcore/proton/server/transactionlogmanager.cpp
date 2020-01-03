// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "configstore.h"
#include "transactionlogmanager.h"
#include <vespa/searchcore/proton/common/eventlogger.h>
#include <vespa/vespalib/util/closuretask.h>
#include <vespa/vespalib/util/exceptions.h>

#include <vespa/log/log.h>
LOG_SETUP(".proton.server.transactionlogmanager");

using vespalib::IllegalStateException;
using vespalib::make_string;
using search::transactionlog::TransLogClient;

namespace proton {


void
TransactionLogManager::doLogReplayComplete(const vespalib::string &domainName,
                                           vespalib::duration elapsedTime) const
{
    EventLogger::transactionLogReplayComplete(domainName, vespalib::count_ms(elapsedTime));
}


TransactionLogManager::TransactionLogManager(const vespalib::string &tlsSpec, const vespalib::string &domainName)
    : TransactionLogManagerBase(tlsSpec, domainName),
      _visitor()
{
}

TransactionLogManager::~TransactionLogManager() = default;

void
TransactionLogManager::init(SerialNum oldestConfigSerial, SerialNum &prunedSerialNum, SerialNum &serialNum)
{
    StatusResult res = TransactionLogManagerBase::init();
    prunedSerialNum = res.serialBegin > 0 ? (res.serialBegin - 1) : 0;
    serialNum = res.serialEnd;
    if (oldestConfigSerial != 0) {
        prunedSerialNum = std::max(prunedSerialNum, oldestConfigSerial);
    }
}

namespace {

void getStatus(TransLogClient::Session & session,
               search::SerialNum & serialBegin,
               search::SerialNum & serialEnd,
               size_t & count)
{
    if (!session.status(serialBegin, serialEnd, count)) {
        throw IllegalStateException(
                make_string(
                    "Could not get status from session with"
                    " domain '%s' on TLS '%s'",
                    session.getDomain().c_str(),
                    session.getTLC().getRPCTarget().c_str()));
    }
}

void getStatus(TransLogClient & client,
               const vespalib::string & domainName,
               search::SerialNum & serialBegin,
               search::SerialNum & serialEnd,
               size_t & count)
{
    TransLogClient::Session::UP session = client.open(domainName);
    if ( ! session) {
        throw IllegalStateException(
                make_string(
                    "Could not open session with domain '%s' on TLS '%s'",
                    session->getDomain().c_str(),
                    session->getTLC().getRPCTarget().c_str()));
    }
    getStatus(*session, serialBegin, serialEnd, count);
}

}

void
TransactionLogManager::prepareReplay(TransLogClient &client,
                                     const vespalib::string &domainName,
                                     SerialNum flushedIndexMgrSerial,
                                     SerialNum flushedSummaryMgrSerial,
                                     ConfigStore &config_store)
{
    SerialNum oldestConfigSerial = config_store.getOldestSerialNum();
    SerialNum from = flushedIndexMgrSerial;
    SerialNum to = flushedSummaryMgrSerial;
    assert(oldestConfigSerial != 0);
    from = std::max(from, oldestConfigSerial);
    if (from < to) {
        SerialNum serialBegin = 0;
        SerialNum serialEnd = 0;
        size_t count = 0;
        getStatus(client, domainName, serialBegin, serialEnd, count);
        SerialNum prunedToken = serialBegin > 0 ? (serialBegin - 1) : 0;
        from = std::max(from, prunedToken);
        if (serialEnd < flushedSummaryMgrSerial) {
            throw IllegalStateException(
                    make_string("SummaryStore '%" PRIu64 "' is more recent than "
                                "transactionlog '%" PRIu64 "'. Immpossible !!",
                                flushedSummaryMgrSerial, serialEnd));
        }
        if (serialEnd < flushedIndexMgrSerial) {
            throw IllegalStateException(
                    make_string("IndexStore '%" PRIu64 "' is more recent than "
                                "transactionlog '%" PRIu64 "'. Immpossible !!",
                                flushedIndexMgrSerial, serialEnd));
        }
    }
}


TlsReplayProgress::UP
TransactionLogManager::startReplay(SerialNum first,
                                   SerialNum syncToken,
                                   TransLogClient::Session::Callback &callback)
{
    assert( !_visitor);
    _visitor = createTlcVisitor(callback);
    if (!_visitor) {
        throw IllegalStateException(
                make_string(
                    "Could not create visitor for "
                    "replaying domain '%s' on TLS '%s'",
                    getDomainName().c_str(), getRpcTarget().c_str()));
    }
    TransactionLogManagerBase::internalStartReplay();

    if (LOG_WOULD_LOG(event)) {
        EventLogger::transactionLogReplayStart(
                getDomainName(), first, syncToken);
    }
    if (!_visitor->visit(first, syncToken)) {
        throw IllegalStateException(
                make_string(
                    "Could not start visitor for "
                    "replaying domain '%s<%" PRIu64 ", %" PRIu64 "]' on TLS '%s'",
                    getDomainName().c_str(),
                    first, syncToken, getRpcTarget().c_str()));
    }
    return TlsReplayProgress::UP(new TlsReplayProgress(getDomainName(), first, syncToken));
}


void
TransactionLogManager::replayDone()
{
    assert(_visitor);
    LOG(debug, "Transaction log replayed for domain '%s'", getDomainName().c_str());
    changeReplayDone();
    LOG(debug, "Broadcasted replay done for domain '%s'", getDomainName().c_str());
    if (LOG_WOULD_LOG(event)) {
        logReplayComplete();
    }
    _visitor.reset();
}


} // namespace proton
