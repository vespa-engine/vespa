// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "visitor.h"
#include "visitormetrics.h"
#include <vespa/storageframework/generic/clock/timer.h>
#include <vespa/storageapi/message/datagram.h>
#include <vespa/storage/persistence/messages.h>
#include <vespa/documentapi/messagebus/messages/visitor.h>
#include <vespa/document/select/node.h>
#include <vespa/vespalib/stllike/hash_map.hpp>
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <unordered_map>
#include <sstream>

#include <vespa/log/log.h>
LOG_SETUP(".visitor.instance");

using document::BucketSpace;

namespace storage {

Visitor::HitCounter::HitCounter(const document::OrderingSpecification* ordering)
    : _firstPassHits(0),
      _firstPassBytes(0),
      _secondPassHits(0),
      _secondPassBytes(0),
      _ordering(ordering)
{
}

void
Visitor::HitCounter::addHit(const document::DocumentId& hit, uint32_t size)
{
    bool firstPass = false;

    if (_ordering && _ordering->getWidthBits() > 0
        && hit.getScheme().getType() == document::IdString::ORDERDOC)
    {
        const document::OrderDocIdString& order(
                static_cast<const document::OrderDocIdString&>(hit.getScheme()));

        int32_t width = (1 << order.getWidthBits());
        int32_t division = (1 << order.getDivisionBits());

        if (_ordering->getOrder() == document::OrderingSpecification::ASCENDING) {
            uint64_t upperLimit = UINT64_MAX;
            if (_ordering->getOrderingStart() < upperLimit - (width - division)) {
                upperLimit = _ordering->getOrderingStart() + width - division;
            }
            if (order.getOrdering() >= _ordering->getOrderingStart() &&
                order.getOrdering() <= upperLimit) {
                firstPass = true;
                /*std::cerr << "First pass because ordering (+) "
                          << order.getOrdering() << " is between "
                          << _ordering->getOrderingStart()
                          << " and " << upperLimit << "\n";*/
            } else {
                /*std::cerr << "Not first pass because ordering (+) "
                          << order.getOrdering() << " is not between "
                          << _ordering->getOrderingStart()
                          << " and " << upperLimit << "\n";*/
            }
        } else {
            uint64_t lowerLimit = 0;
            if (_ordering->getOrderingStart() > (uint64_t)(width - division)) {
                lowerLimit = _ordering->getOrderingStart() - (width - division);
            }
            if (order.getOrdering() <= _ordering->getOrderingStart() &&
                order.getOrdering() >= lowerLimit) {
                firstPass = true;
                /*std::cerr << "First pass because ordering (-) "
                          << order.getOrdering() << " is between "
                          << lowerLimit << " and "
                          << _ordering->getOrderingStart() << "\n";*/
            } else {
                /*std::cerr << "Not first pass because ordering (-) "
                          << order.getOrdering() << " is not between "
                          << lowerLimit << " and "
                          << _ordering->getOrderingStart() << "\n";*/
            }
        }
    } else {
//        std::cerr << "Not counting first pass: " << _ordering->getWidthBits() << "\n";
    }

    if (firstPass) {
        _firstPassHits++;
        _firstPassBytes += size;
    } else {
        _secondPassHits++;
        _secondPassBytes += size;
    }

}

void
Visitor::HitCounter::updateVisitorStatistics(
        vdslib::VisitorStatistics& statistics)
{
    statistics.setDocumentsReturned(
            statistics.getDocumentsReturned() + _firstPassHits);
    statistics.setBytesReturned(
            statistics.getBytesReturned() + _firstPassBytes);
    statistics.setSecondPassDocumentsReturned(
            statistics.getSecondPassDocumentsReturned() + _secondPassHits);
    statistics.setSecondPassBytesReturned(
            statistics.getSecondPassBytesReturned() + _secondPassBytes);
}

Visitor::VisitorTarget::MessageMeta::MessageMeta(
        uint64_t msgId,
        std::unique_ptr<documentapi::DocumentMessage> msg)
    : messageId(msgId),
      retryCount(0),
      memoryUsage(msg->getApproxSize()),
      message(std::move(msg)),
      messageText(message->toString())
{
}

Visitor::VisitorTarget::MessageMeta::MessageMeta(
        Visitor::VisitorTarget::MessageMeta&& rhs) noexcept
    : messageId(rhs.messageId),
      retryCount(rhs.retryCount),
      memoryUsage(rhs.memoryUsage),
      message(std::move(rhs.message)),
      messageText(std::move(rhs.messageText))
{
}

Visitor::VisitorTarget::MessageMeta::~MessageMeta()
{
}

Visitor::VisitorTarget::MessageMeta&
Visitor::VisitorTarget::MessageMeta::operator=(
        Visitor::VisitorTarget::MessageMeta&& rhs) noexcept
{
    messageId = rhs.messageId;
    retryCount = rhs.retryCount;
    memoryUsage = rhs.memoryUsage;
    message = std::move(rhs.message);
    messageText = std::move(rhs.messageText);
    return *this;
}

Visitor::VisitorTarget::MessageMeta&
Visitor::VisitorTarget::insertMessage(
        std::unique_ptr<documentapi::DocumentMessage> msg)
{
    ++_pendingMessageId;
    const uint64_t id = _pendingMessageId;
    MessageMeta value(id, std::move(msg));
    _memoryUsage += value.memoryUsage;
    auto inserted = _messageMeta.insert(std::make_pair(id, std::move(value)));
    assert(inserted.second);
    return inserted.first->second;
}

Visitor::VisitorTarget::MessageMeta
Visitor::VisitorTarget::releaseMetaForMessageId(uint64_t msgId)
{
    auto iter = _messageMeta.find(msgId);
    assert(iter != _messageMeta.end());
    MessageMeta meta = std::move(iter->second);
    assert(_memoryUsage >= meta.memoryUsage);
    _memoryUsage -= meta.memoryUsage;
    _messageMeta.erase(iter);
    return meta;
}

void
Visitor::VisitorTarget::reinsertMeta(MessageMeta meta)
{
    _memoryUsage += meta.memoryUsage;
    auto inserted = _messageMeta.insert(
            std::make_pair(meta.messageId, std::move(meta)));
    (void) inserted;
    assert(inserted.second);
}

Visitor::VisitorTarget::MessageMeta&
Visitor::VisitorTarget::metaForMessageId(uint64_t msgId)
{
    return _messageMeta.find(msgId)->second;
}

void
Visitor::VisitorTarget::discardQueuedMessages()
{
    for (MessageQueue::iterator
             it(_queuedMessages.begin()), e(_queuedMessages.end());
         it != e; ++it)
    {
        LOG(spam, "Erasing queued message with id %zu", it->second);
        releaseMetaForMessageId(it->second);
    }
    _queuedMessages.clear();
}

Visitor::BucketIterationState::~BucketIterationState()
{
    if (_iteratorId != 0) {
        // Making the assumption that this is effectively nothrow.
        std::shared_ptr<DestroyIteratorCommand> cmd(
                new DestroyIteratorCommand(_iteratorId));
        cmd->setLoadType(_visitor._initiatingCmd->getLoadType());
        cmd->getTrace().setLevel(_visitor._traceLevel);
        cmd->setPriority(0);

        LOG(debug, "Visitor '%s' sending DestroyIteratorCommand for %s, "
            "iterator id %zu.",
            _visitor._id.c_str(),
            _bucket.getBucketId().toString().c_str(),
            uint64_t(_iteratorId));
        _messageHandler.send(cmd, _visitor);
    }
}

Visitor::VisitorOptions::VisitorOptions()
    : _fromTime(0),
      _toTime(framework::MicroSecTime::max()),
      _maxParallel(1),
      _maxParallelOneBucket(2),
      _maxPending(1),
      _fieldSet("[all]"),
      _visitRemoves(false)
{
}

Visitor::VisitorTarget::VisitorTarget()
    : _pendingMessageId(0),
      _memoryUsage(0)
{
}

Visitor::VisitorTarget::~VisitorTarget() {}

Visitor::Visitor(StorageComponent& component)
    : _component(component),
      _visitorOptions(),
      _visitorTarget(),
      _state(STATE_NOT_STARTED),
      _buckets(),
      _bucketSpace(BucketSpace::invalid()),
      _currentBucket(),
      _bucketStates(),
      _calledStartingVisitor(false),
      _calledCompletedVisitor(false),
      _startTime(_component.getClock().getTimeInMicros()),
      _hasSentReply(false),
      _docBlockSize(1024),
      _memoryUsageLimit(UINT32_MAX),
      _docBlockTimeout(180 * 1000),
      _visitorInfoTimeout(60 * 1000),
      _serialNumber(0),
      _traceLevel(0),
      _ownNodeIndex(0xffff),
      _visitorCmdId(0),
      _visitorId(0),
      _priority(api::StorageMessage::NORMAL),
      _result(api::ReturnCode::OK),
      _trace(DEFAULT_TRACE_MEMORY_LIMIT),
      _messageHandler(0),
      _id(),
      _controlDestination(),
      _dataDestination(),
      _documentSelection()
{
}

Visitor::~Visitor()
{
    assert(_bucketStates.empty());
}

void
Visitor::sendMessage(documentapi::DocumentMessage::UP cmd)
{
    assert(cmd.get());
    if (!isRunning()) return;
    cmd->setRoute(_dataDestination->getRoute());

    cmd->setPriority(_documentPriority);
    cmd->setLoadType(_initiatingCmd->getLoadType());

    framework::MicroSecTime time(_component.getClock().getTimeInMicros());

    if (time + _docBlockTimeout.getMicros() > _timeToDie) {
        cmd->setTimeRemaining((_timeToDie > time)
                              ? (_timeToDie - time).getMillis().getTime()
                              : 0);
    } else {
        cmd->setTimeRemaining(_docBlockTimeout.getTime());
    }
    cmd->getTrace().setLevel(_traceLevel);

    auto& msgMeta = _visitorTarget.insertMessage(std::move(cmd));
    sendDocumentApiMessage(msgMeta);
}

void
Visitor::sendDocumentApiMessage(VisitorTarget::MessageMeta& msgMeta) {
    documentapi::DocumentMessage& cmd(*msgMeta.message);
    // Just enqueue if it's not time to send this message yet
    if (_messageSession->pending() >= _visitorOptions._maxPending
        && cmd.getType() != documentapi::DocumentProtocol::MESSAGE_VISITORINFO)
    {
        MBUS_TRACE(cmd.getTrace(), 5, vespalib::make_string(
                           "Enqueueing message because the visitor already "
                           "had %d pending messages",
                           _visitorOptions._maxPending));

        LOG(spam,
            "Visitor '%s' enqueueing message with id %zu",
            _id.c_str(),
            msgMeta.messageId);
        _visitorTarget._queuedMessages.insert(std::make_pair(
                    framework::MicroSecTime(0), msgMeta.messageId));
    } else {
        LOG(spam,
            "Visitor '%s' immediately sending message '%s' with id %zu",
            _id.c_str(),
            cmd.toString().c_str(),
            msgMeta.messageId);
        cmd.setContext(msgMeta.messageId);
        mbus::Result res(_messageSession->send(std::move(msgMeta.message)));
        if (res.isAccepted()) {
            _visitorTarget._pendingMessages.insert(msgMeta.messageId);
        } else {
            LOG(warning,
                "Visitor '%s' failed to send DocumentAPI message: %s",
                _id.c_str(),
                res.getError().toString().c_str());
            api::ReturnCode returnCode(
                    static_cast<api::ReturnCode::Result>(
                            res.getError().getCode()),
                    res.getError().getMessage());
            fail(returnCode, true);
            close();
        }
    }
}

void
Visitor::sendInfoMessage(documentapi::VisitorInfoMessage::UP cmd)
{
    assert(cmd.get());
    if (!isRunning()) return;

    if (_controlDestination->toString().length()) {
        cmd->setRoute(_controlDestination->getRoute());
        cmd->setPriority(_documentPriority);
        cmd->setTimeRemaining(_visitorInfoTimeout.getTime());
        auto& msgMeta = _visitorTarget.insertMessage(std::move(cmd));
        sendDocumentApiMessage(msgMeta);
    }
}

void
Visitor::close()
{
    if (_state != STATE_COMPLETED) {
        transitionTo(STATE_CLOSING);
    }
    sendReplyOnce();
}

const char*
Visitor::getStateName(VisitorState s)
{
    switch (s) {
    case STATE_NOT_STARTED:
        return "NOT_STARTED";
    case STATE_RUNNING:
        return "RUNNING";
    case STATE_CLOSING:
        return "CLOSING";
    case STATE_COMPLETED:
        return "COMPLETED";
    default:
        assert(!"Unknown visitor state");
        return NULL;
    }
}

Visitor::VisitorState
Visitor::transitionTo(VisitorState newState)
{
    LOG(debug, "Visitor '%s' state transition %s -> %s",
        _id.c_str(),
        getStateName(_state),
        getStateName(newState));
    VisitorState oldState = _state;
    _state = newState;
    return oldState;
}

bool
Visitor::mayTransitionToCompleted() const
{
    return (!isRunning()
            && !hasPendingIterators()
            && _visitorTarget._pendingMessages.empty()
            && _visitorTarget._queuedMessages.empty()
            && _messageSession->pending() == 0);
}

void
Visitor::forceClose()
{
    for (std::list<BucketIterationState*>::iterator it = _bucketStates.begin();
         it != _bucketStates.end(); ++it)
    {
        // Reset iterator id so no destroy iterator will be sent
        (*it)->setIteratorId(spi::IteratorId(0));
        delete *it;
    }
    _bucketStates.clear();
    transitionTo(STATE_COMPLETED);
}

void
Visitor::sendReplyOnce()
{
    assert(_initiatingCmd.get());
    if (!_hasSentReply) {
        std::shared_ptr<api::StorageReply> reply(
                _initiatingCmd->makeReply().release());

        _hitCounter->updateVisitorStatistics(_visitorStatistics);
        static_cast<api::CreateVisitorReply*>(reply.get())
            ->setVisitorStatistics(_visitorStatistics);
        if (shouldAddMbusTrace()) {
            _trace.moveTraceTo(reply->getTrace().getRoot());
        }
        reply->setResult(_result);
        LOG(debug, "Sending %s", reply->toString(true).c_str());
        _messageHandler->send(reply);
        _hasSentReply = true;
    }
}

void
Visitor::finalize()
{
    if (_state != STATE_COMPLETED) {
        LOG(error, "Attempting to finalize non-completed visitor %s",
            _id.c_str());
        assert(false);
    }
    assert(_bucketStates.empty());

    if (_result.success()) {
        if (_messageSession->pending() > 0)
        {
            _result = api::ReturnCode(api::ReturnCode::ABORTED);
            try{
                abortedVisiting();
            } catch (std::exception& e) {
                LOG(warning, "Visitor %s had a problem in abortVisiting(). As "
                    "visitor is already complete, this has been ignored: %s",
                    _id.c_str(), e.what());
            }
        }
    }
    sendReplyOnce();
    _initiatingCmd.reset();
}

/**
 * If a bucket state has no pending iterators or control commands,
 * we can safely discard it when a visitor fails. No need to push
 * more traffic to the persistence layer.
 */
void
Visitor::discardAllNoPendingBucketStates()
{
    for (BucketStateList::iterator
             it(_bucketStates.begin()), e(_bucketStates.end());
         it != e;)
    {
        BucketIterationState& bstate(**it);
        if (bstate.hasPendingControlCommand() || bstate.hasPendingIterators()) {
            LOG(debug,
                "Visitor '%s' not discarding bucket state %s "
                "since it has pending operations",
                _id.c_str(),
                bstate.toString().c_str());
            ++it;
            continue;
        }
        LOG(debug, "Visitor '%s' discarding bucket state %s",
            _id.c_str(), bstate.toString().c_str());
        delete *it;
        it = _bucketStates.erase(it);
    }
}

void
Visitor::fail(const api::ReturnCode& reason,
              bool overrideExistingError)
{
    assert(_state != STATE_COMPLETED);
    if (_result.getResult() < reason.getResult() || overrideExistingError) {
        LOG(debug, "Setting result of visitor '%s' to %s",
            _id.c_str(), reason.toString().c_str());
        _result = reason;
    }
    if (_visitorTarget.hasQueuedMessages()) {
        LOG(debug, "Visitor '%s' dropping %zu queued messages bound to %s "
            "since visitor has failed",
            _id.c_str(),
            _visitorTarget._queuedMessages.size(),
            _controlDestination->toString().c_str());
        _visitorTarget.discardQueuedMessages();
    }
    discardAllNoPendingBucketStates();
    transitionTo(STATE_CLOSING);
}

bool
Visitor::shouldReportProblemToClient(const api::ReturnCode& code,
                                     size_t retryCount) const
{
    // Report _once_ per message if we reach a certain retry threshold.
    if (retryCount == TRANSIENT_ERROR_RETRIES_BEFORE_NOTIFY) {
        return true;
    }
    return !(code.isBucketDisappearance()
             || code.isBusy()
             || code == api::ReturnCode::WRONG_DISTRIBUTION);
}

void
Visitor::reportProblem(const std::string& problem)
{
    framework::MicroSecTime time(_component.getClock().getTimeInMicros());
    std::map<std::string, framework::MicroSecTime>::iterator it(
            _recentlySentErrorMessages.find(problem));
        // Ignore errors already reported last minute
    if (it != _recentlySentErrorMessages.end() &&
        it->second + framework::MicroSecTime(60*1000*1000) > time)
    {
        return;
    }
    LOG(debug, "Visitor '%s' sending VisitorInfo with message \"%s\" to %s",
        _id.c_str(),
        problem.c_str(),
        _controlDestination->toString().c_str());
    _recentlySentErrorMessages[problem] = time;
    documentapi::VisitorInfoMessage::UP cmd(
            new documentapi::VisitorInfoMessage());
    cmd->setErrorMessage(problem);
    sendInfoMessage(std::move(cmd));

    // Clear list if it grows too large
    if (_recentlySentErrorMessages.size() > 40) {
        _recentlySentErrorMessages.clear();
    }
}

void
Visitor::reportProblem(const api::ReturnCode& problemCode)
{
    vespalib::asciistream os;
    os << "[From content node " << _ownNodeIndex << "] ";
    os << api::ReturnCode::getResultString(problemCode.getResult())
       << ": " << problemCode.getMessage();
    reportProblem(os.str());
}

void
Visitor::start(api::VisitorId id, api::StorageMessage::Id cmdId,
               const std::string& name,
               const std::vector<document::BucketId>& buckets,
               framework::MicroSecTime fromTimestamp,
               framework::MicroSecTime toTimestamp,
               std::unique_ptr<document::select::Node> docSelection,
               const std::string& docSelectionString,
               std::unique_ptr<document::OrderingSpecification> ordering,
               VisitorMessageHandler& handler,
               VisitorMessageSession::UP messageSession,
               documentapi::Priority::Value documentPriority)
{
    assert(_state == STATE_NOT_STARTED);
    _visitorId = id;
    _visitorCmdId = cmdId;
    _id = name;
    _messageHandler = &handler;
    _ordering = std::move(ordering);
    _documentSelection.reset(docSelection.release());
    _documentSelectionString = docSelectionString;
    _buckets = buckets;
    _visitorOptions._fromTime = fromTimestamp;
    _visitorOptions._toTime = toTimestamp;
    _currentBucket = 0;
    _hitCounter.reset(new HitCounter(_ordering.get()));
    _messageSession = std::move(messageSession);
    _documentPriority = documentPriority;

    _state = STATE_RUNNING;

    LOG(debug, "Starting visitor '%s' for %" PRIu64 " buckets from %" PRIu64 " to "
               "%" PRIu64 ". First is %s. Max pending replies: %u, include "
               "removes: %s, field set: %s.",
        _id.c_str(),
        _buckets.size(),
        _visitorOptions._fromTime.getTime(),
        _visitorOptions._toTime.getTime(),
        (buckets.size() > 0 ? _buckets[0].toString().c_str() : ""),
        _visitorOptions._maxPending,
        (_visitorOptions._visitRemoves ? "true" : "false"),
        _visitorOptions._fieldSet.c_str());
}

void
Visitor::attach(std::shared_ptr<api::StorageCommand> initiatingCmd,
                const api::StorageMessageAddress& controlAddress,
                const api::StorageMessageAddress& dataAddress,
                framework::MilliSecTime timeout)
{
    _priority = initiatingCmd->getPriority();
    _timeToDie = _component.getClock().getTimeInMicros() + timeout.getMicros();
    if (_initiatingCmd.get()) {
        std::shared_ptr<api::StorageReply> reply(
                _initiatingCmd->makeReply().release());
        reply->setResult(api::ReturnCode::ABORTED);
        _messageHandler->send(reply);
    }
    _initiatingCmd = initiatingCmd;
    _traceLevel = _initiatingCmd->getTrace().getLevel();
    {
        // Set new address
        _controlDestination.reset(
                new api::StorageMessageAddress(controlAddress));
        _dataDestination.reset(new api::StorageMessageAddress(dataAddress));
    }
    LOG(debug, "Visitor '%s' has control destination %s and data "
               "destination %s.",
        _id.c_str(), _controlDestination->toString().c_str(),
        _dataDestination->toString().c_str());
    if (!_calledStartingVisitor) {
        _calledStartingVisitor = true;
        try{
            startingVisitor(_buckets);
        } catch (std::exception& e) {
            std::ostringstream ost;
            ost << "Failed to start visitor: " << e.what();
            fail(api::ReturnCode(api::ReturnCode::ABORTED, ost.str()));
            return;
        }
    }

    // In case there was no messages to resend we need to call
    // continueVisitor to provoke it to resume.
    for (uint32_t i=0; i<_visitorOptions._maxParallelOneBucket; ++i) {
        if (!continueVisitor()) return;
    }
}

bool
Visitor::addBoundedTrace(uint32_t level, const vespalib::string &message) {
    mbus::Trace tempTrace;
    tempTrace.trace(level, message);
    return _trace.add(tempTrace.getRoot());
}

void
Visitor::handleDocumentApiReply(mbus::Reply::UP reply,
                        VisitorThreadMetrics& metrics)
{
    if (shouldAddMbusTrace()) {
        _trace.add(reply->getTrace().getRoot());
    }

    mbus::Message::UP message = reply->getMessage();
    uint64_t messageId = reply->getContext().value.UINT64;
    uint32_t removed = _visitorTarget._pendingMessages.erase(messageId);

    LOG(spam, "Visitor '%s' reply %s for message ID %" PRIu64, _id.c_str(),
            reply->toString().c_str(), messageId);

    assert(removed == 1);
    (void) removed;
    // Always remove message from target mapping. We will reinsert it if the
    // message needs to be retried.
    auto meta = _visitorTarget.releaseMetaForMessageId(messageId);

    if (!reply->hasErrors()) {
        metrics.averageMessageSendTime[getLoadType()].addValue(
                (message->getTimeRemaining() - message->getTimeRemainingNow()) / 1000.0);
        LOG(debug, "Visitor '%s' reply %s for message ID %" PRIu64 " was OK", _id.c_str(),
            reply->toString().c_str(), messageId);

        continueVisitor();
        return;
    }

    metrics.visitorDestinationFailureReplies[getLoadType()].inc();

    if (message->getType() == documentapi::DocumentProtocol::MESSAGE_VISITORINFO) {
        LOG(debug, "Aborting visitor as we failed to talk to "
                           "controller: %s",
                    reply->getError(0).toString().c_str());
        api::ReturnCode returnCode(
                static_cast<api::ReturnCode::Result>(
                        reply->getError(0).getCode()),
                reply->getError(0).getMessage());
        fail(returnCode, true);
        close();
        return;
    }

    api::ReturnCode returnCode(
            static_cast<api::ReturnCode::Result>(reply->getError(0).getCode()),
            reply->getError(0).getMessage());
    if (returnCode.isCriticalForVisitor()) {
        // Abort - something is wrong with target.
        fail(returnCode, true);
        close();
        return;
    }

    if (failed()) {
        LOG(debug, "Failed to send message from visitor '%s', due to "
            "%s. Not resending since visitor has failed",
            _id.c_str(), returnCode.toString().c_str());
        return;
    }
    assert(!meta.message);
    meta.message.reset(
            static_cast<documentapi::DocumentMessage*>(message.release()));
    meta.retryCount++;
    const size_t retryCount = meta.retryCount;

    // Tag time for later resending. nextSendAttemptTime != 0 indicates
    // that the message is not pending, but should be sent later.
    framework::MicroSecTime delay(
            (1 << std::min(12u, meta.retryCount)) * 10000);

    _visitorTarget.reinsertMeta(std::move(meta));
    _visitorTarget._queuedMessages.insert(
            std::make_pair(_component.getClock().getTimeInMicros() + delay,
                           messageId));
    if (shouldReportProblemToClient(returnCode, retryCount)) {
        reportProblem(returnCode);
    }

    // Creates delay in the following fashion based on retry count.
    // Max delay is then 40 seconds. At which time, retrying should not
    // use up that much resources.
    // 20, 40, 80, 160, 320, 640, 1280, 2560, 5120, 10240, 20480, 40960
    LOG(debug, "Failed to send message from visitor '%s', due to "
        "%s. Resending in %" PRIu64 " ms",
        _id.c_str(), returnCode.toString().c_str(),
        delay.getMillis().getTime());
}

void
Visitor::onCreateIteratorReply(
        const std::shared_ptr<CreateIteratorReply>& reply,
        VisitorThreadMetrics& /*metrics*/)
{
    std::list<BucketIterationState*>::reverse_iterator it = _bucketStates.rbegin();

    document::Bucket bucket(reply->getBucket());
    document::BucketId bucketId(bucket.getBucketId());
    for (; it != _bucketStates.rend(); ++it) {
        if ((*it)->getBucketId() == bucketId) {
            break;
        }
    }
    assert(it != _bucketStates.rend());
    BucketIterationState& bucketState(**it);

    if (reply->getResult().failed()) {
        LOG(debug, "Failed to create iterator for bucket %s: %s",
            bucketId.toString().c_str(),
            reply->getResult().toString().c_str());
        fail(reply->getResult());
        delete *it;
        _bucketStates.erase((++it).base());
        return;
    }
    bucketState.setIteratorId(reply->getIteratorId());
    if (failed()) {
        LOG(debug, "Create iterator for bucket %s is OK, "
            "but visitor has failed: %s",
            bucketId.toString().c_str(),
            _result.toString().c_str());
        delete *it;
        _bucketStates.erase((++it).base());
        return;
    }

    LOG(debug, "Visitor '%s' starting to visit bucket %s.",
        _id.c_str(), bucketId.toString().c_str());
    auto cmd = std::make_shared<GetIterCommand>(bucket, bucketState.getIteratorId(), _docBlockSize);
    cmd->setLoadType(_initiatingCmd->getLoadType());
    cmd->getTrace().setLevel(_traceLevel);
    cmd->setPriority(_priority);
    ++bucketState._pendingIterators;
    _messageHandler->send(cmd, *this);
}

void
Visitor::onGetIterReply(const std::shared_ptr<GetIterReply>& reply,
                        VisitorThreadMetrics& metrics)
{
    LOG(debug, "Visitor '%s' got get iter reply for bucket %s: %s",
               _id.c_str(),
               reply->getBucketId().toString().c_str(),
               reply->getResult().toString().c_str());
    std::list<BucketIterationState*>::reverse_iterator it = _bucketStates.rbegin();

    // New requests will be pushed on end of list.. So searching
    // in reverse order should quickly get correct result.
    for (; it != _bucketStates.rend(); ++it) {
        if ((*it)->getBucketId() == reply->getBucketId()) {
            break;
        }
    }
    assert(it != _bucketStates.rend());

    if (reply->getResult().failed() || !isRunning()) {
        // Don't log warnings for BUCKET_NOT_FOUND and BUCKET_DELETED,
        // since this can happen during normal splits.
        // Don't log for ABORT, due to storage shutdown.
        if (!reply->getResult().success() &&
            !reply->getResult().isShutdownRelated() &&
            !reply->getResult().isBucketDisappearance())
        {
            LOG(warning, "Failed to talk to persistence layer for bucket "
                         "%s. Aborting visitor '%s': %s",
                reply->getBucketId().toString().c_str(),
                _id.c_str(), reply->getResult().toString().c_str());
        }
        fail(reply->getResult());
        BucketIterationState& bucketState(**it);
        assert(bucketState._pendingIterators > 0);
        --bucketState._pendingIterators;
        if (bucketState._pendingIterators == 0) {
            delete *it;
            _bucketStates.erase((++it).base());
        }
        return;
    }

    BucketIterationState& bucketState(**it);
    bucketState.setCompleted(reply->isCompleted());
    --bucketState._pendingIterators;
    if (!reply->getEntries().empty()) {
        LOG(debug, "Processing documents in handle given from bucket %s.",
            reply->getBucketId().toString().c_str());
        // While handling documents we should not keep locks, such
        // that visitor may process several things at once.
        if (isRunning()) {
            MBUS_TRACE(reply->getTrace(), 5,
                       vespalib::make_string("Visitor %s handling block of %zu documents.",
                                             _id.c_str(),
                                             reply->getEntries().size()));
            LOG(debug, "Visitor %s handling block of %zu documents.",
                _id.c_str(),
                reply->getEntries().size());
            try{
                framework::MilliSecTimer processingTimer(_component.getClock());
                handleDocuments(reply->getBucketId(),
                                reply->getEntries(),
                                *_hitCounter);
                metrics.averageProcessingTime[reply->getLoadType()]
                    .addValue(processingTimer.getElapsedTimeAsDouble());

                MBUS_TRACE(reply->getTrace(), 5, "Done processing data block in visitor plugin");

                uint64_t size = 0;
                for (size_t i = 0; i < reply->getEntries().size(); ++i) {
                    size += reply->getEntries()[i]->getPersistedDocumentSize();
                }

                _visitorStatistics.setDocumentsVisited(
                        _visitorStatistics.getDocumentsVisited()
                        + reply->getEntries().size());
                _visitorStatistics.setBytesVisited(
                        _visitorStatistics.getBytesVisited() + size);
            } catch (std::exception& e) {
                LOG(warning, "handleDocuments threw exception %s",
                    e.what());
                reportProblem(e.what());
            }
        }
    } else {
        LOG(debug, "No documents to process in handle given for bucket %s.",
            reply->getBucketId().toString().c_str());
    }

    if (shouldAddMbusTrace()) {
        _trace.add(reply->getTrace().getRoot());
    }

    LOG(debug, "Continuing visitor %s.", _id.c_str());
    continueVisitor();
}

void
Visitor::sendDueQueuedMessages(framework::MicroSecTime timeNow)
{
    // Assuming few messages in sent queue, so cheap to go through all.
    while (!_visitorTarget._queuedMessages.empty()
           && (_visitorTarget._pendingMessages.size()
               < _visitorOptions._maxPending)) {
        VisitorTarget::MessageQueue::iterator it(
                _visitorTarget._queuedMessages.begin());
        if (it->first < timeNow) {
            auto& msgMeta = _visitorTarget.metaForMessageId(it->second);
            _visitorTarget._queuedMessages.erase(it);
            sendDocumentApiMessage(msgMeta);
        } else {
            break;
        }
    }
}

bool
Visitor::continueVisitor()
{
    if (mayTransitionToCompleted()) {
        transitionTo(STATE_COMPLETED);
        return false;
    }
    framework::MicroSecTime time(_component.getClock().getTimeInMicros());
    if (time > _timeToDie) { // If we have timed out, just shut down.
        if (isRunning()) {
            LOG(debug, "Visitor %s timed out. Closing it.", _id.c_str());
            fail(api::ReturnCode(api::ReturnCode::ABORTED,
                                 "Visitor timed out"));
            close();
        }
        return false;
    }

    sendDueQueuedMessages(time);

    // No need to do more work if we already have maximum pending towards data handler
    if (_messageSession->pending() + _visitorTarget._queuedMessages.size()
        >= _visitorOptions._maxPending)
    {
        LOG(spam, "Number of pending messages (%zu pending, %zu queued) "
            "already >= max pending (%u)",
            _visitorTarget._pendingMessages.size(),
            _visitorTarget._queuedMessages.size(),
            _visitorOptions._maxPending);
        return false;
    }

    if (_visitorTarget.getMemoryUsage() >= _memoryUsageLimit) {
        LOG(spam,
            "Visitor already using maximum amount of memory "
            "(using %u, limit %u)",
            _visitorTarget.getMemoryUsage(),
            _memoryUsageLimit);
        return false;
    }

    // If there are no more buckets to visit and no pending messages
    // to the client, mark visitor as complete.
    if (!getIterators()) {
        if (_visitorTarget._pendingMessages.empty()
            && _visitorTarget._queuedMessages.empty())
        {
            if (isRunning()) {
                LOG(debug, "Visitor '%s' has not been aborted", _id.c_str());
                if (!_calledCompletedVisitor) {
                    VISITOR_TRACE(7, "Visitor marked as complete, calling completedVisiting()");
                    _calledCompletedVisitor = true;
                    try{
                        completedVisiting(*_hitCounter);
                    } catch (std::exception& e) {
                        LOG(warning, "Visitor %s failed in completedVisiting() "
                            "callback.  As visitor is already complete, this "
                            "has been ignored: %s", _id.c_str(), e.what());
                    }
                    VISITOR_TRACE(7, "completedVisiting() has finished");

                    // Visitor could create messages in completed visiting.
                    if (_messageSession->pending() > 0) {
                        return false;
                    }
                }
            }

            LOG(debug, "No pending messages, tagging visitor '%s' complete",
                _id.c_str());
            transitionTo(STATE_COMPLETED);
        } else {
            LOG(debug, "Visitor %s waiting for all commands to be replied to "
                "(pending=%zu, queued=%zu)",
                _id.c_str(),
                _visitorTarget._pendingMessages.size(),
                _visitorTarget._queuedMessages.size());
        }
        return false;
    } else {
        return true;
    }
}

void
Visitor::getStatus(std::ostream& out, bool verbose) const
{
    out << "<table border=\"1\"><tr><td>Property</td><td>Value</td></tr>\n";

    out << "<tr><td>Visitor id</td><td>" << _visitorId << "</td></tr>\n";
    out << "<tr><td>Visitor name</td><td>" << _id << "</td></tr>\n";

    out << "<tr><td>Number of buckets to visit</td><td>" << _buckets.size()
        << "</td></tr>\n";
    out << "<tr><td>Next bucket to visit</td><td>"
        << "#" << _currentBucket << ": ";
    if (_currentBucket >= _buckets.size()) {
        out << "Out of bounds";
    } else {
        out << _buckets[_currentBucket].toString();
    }
    out << "</td></tr>\n";

    out << "<tr><td>State</td><td>\n"
        << getStateName(_state)
        << "</td></tr>\n";

    out << "<tr><td>Current status</td><td>"
        << _result << "</td></tr>\n";

    out << "<tr><td>Failed</td><td>" << (failed() ? "true" : "false")
        << "</td></tr>\n";

    if (verbose) {
        out << "<tr><td>Max messages pending to client</td><td>"
            << _visitorOptions._maxPending
            << "</td></tr>\n";
        out << "<tr><td>Max parallel buckets visited</td><td>"
            << _visitorOptions._maxParallel
            << "</td></tr>\n";
        out << "<tr><td>Max parallel getiter requests per bucket visited"
            << "</td><td>" << _visitorOptions._maxParallelOneBucket
            << "</td></tr>\n";
        out << "<tr><td>Called starting visitor</td><td>"
            << (_calledStartingVisitor ? "true" : "false") << "</td></tr>\n";
        out << "<tr><td>Called completed visitor</td><td>"
            << (_calledCompletedVisitor ? "true" : "false") << "</td></tr>\n";
        out << "<tr><td>Visiting fields</td><td>"
            << _visitorOptions._fieldSet
            << "</td></tr>\n";
        out << "<tr><td>Visiting removes</td><td>"
            << (_visitorOptions._visitRemoves ? "true" : "false")
            << "</td></tr>\n";
        out << "<tr><td>Control destination</td><td>";
        if (_controlDestination.get()) {
            out << _controlDestination->toString();
        } else {
            out << "nil";
        }
        out << "</td></tr>\n";
        out << "<tr><td>Data destination</td><td>";
        if (_dataDestination.get()) {
            out << _dataDestination->toString();
        } else {
            out << "nil";
        }
        out << "</td></tr>\n";
        out << "<tr><td>Document selection</td><td>";
        if (_documentSelection.get()) {
            out << *_documentSelection;
        } else {
            out << "nil";
        }
        out << "</td></tr>\n";

        out << "<tr><td>Time period(" << _visitorOptions._fromTime << ", "
            << _visitorOptions._toTime << "):<br>\n";
        out << "<tr><td>Message id of create visitor command</td><td>"
            << _visitorCmdId << "</td></tr>\n";
        out << "<tr><td>Doc block timeout</td><td>"
            << _docBlockTimeout << "</td></tr>\n";
        out << "<tr><td>Visitor info timeout</td><td>"
            << _visitorInfoTimeout << "</td></tr>\n";
        out << "<tr><td>Visitor priority</td><td>"
            << static_cast<uint32_t>(_priority) << "</td></tr>\n";
        out << "<tr><td>Trace level</td><td>"
            << _traceLevel << "</td></tr>\n";

        framework::MicroSecTime time(_component.getClock().getTimeInMicros());

        out << "<tr><td>Time left until timeout</td><td>";
        if (time <= _timeToDie) {
            out << (_timeToDie - time).getMillis().getTime() << " ms";
        } else {
            out << "(expired "
                << (time - _timeToDie).getMillis().getTime()
                << " ms ago)";
        }
        out << "</td></tr>\n";
    }
    out << "</table>\n";

    out << "<h4>Buckets to visit</h4>";
    typedef std::pair<api::Timestamp, api::Timestamp> TimePair;
    TimePair lastTime;
    for (uint32_t i=0; i<_buckets.size(); ++i) {
        out << _buckets[i] << "\n<br>";
    }

    out << "<h4>States of buckets currently being visited</h4>";
    if (_bucketStates.size() == 0) {
        out << "None\n";
    }
    for (auto* state : _bucketStates) {
        out << "  " << *state << "<br>\n";
    }

    std::unordered_map<uint64_t, framework::MicroSecTime> idToSendTime;
    for (auto& sendTimeToId : _visitorTarget._queuedMessages) {
        idToSendTime[sendTimeToId.second] = sendTimeToId.first;
    }

    out << "<h4>Messages being sent to client</h4>\n";
    out << "<p>Estimated memory usage: "
        << _visitorTarget.getMemoryUsage()
        << "</p>\n";
    for (auto& idAndMeta : _visitorTarget._messageMeta) {
        const VisitorTarget::MessageMeta& meta(idAndMeta.second);
        out << "Message #" << idAndMeta.first << " <b>"
            << meta.messageText << "</b> ";
        if (meta.retryCount > 0) {
            out << "Retried " << meta.retryCount << " times. ";
        }
        if (_visitorTarget._pendingMessages.find(idAndMeta.first)
            != _visitorTarget._pendingMessages.end())
        {
            out << "<i>pending</i>";
        };
        auto queued = idToSendTime.find(idAndMeta.first);
        if (queued != idToSendTime.end()) {
            out << "Scheduled for sending at timestamp "
                << (queued->second.getSeconds());
        }

        out << "<br/>\n";
    }

    out << "\n";
}

bool
Visitor::getIterators()
{
    LOG(debug, "getIterators, visitor %s, _buckets = %zu , _bucketStates = %zu, "
               "_currentBucket = %d",
        _id.c_str(), _buckets.size(),
        _bucketStates.size(), _currentBucket);

    // Don't send any further GetIters if we're closing
    if (!isRunning()) {
        if (hasPendingIterators()) {
            LOG(debug, "Visitor has failed but waiting for %zu "
                "buckets to finish processing",
                _bucketStates.size());
            return true;
        } else {
            return false;
        }
    }

    // Go through buckets found. Take the first that doesn't have requested
    // state and request a new piece.
    for (std::list<BucketIterationState*>::iterator it = _bucketStates.begin();
         it != _bucketStates.end();)
    {
        assert(*it);
        BucketIterationState& bucketState(**it);
        if ((bucketState._pendingIterators
             >= _visitorOptions._maxParallelOneBucket)
            || bucketState.hasPendingControlCommand())
        {
            ++it;
            continue;
        }
        if (bucketState.isCompleted()) {
            if (bucketState._pendingIterators > 0) {
                // Wait to process finished with bucket stuff until we have
                // gotten responses for all the getIters pending to bucket
                ++it;
                continue;
            }
            try{
                completedBucket(bucketState.getBucketId(), *_hitCounter);
                _visitorStatistics.setBucketsVisited(
                        _visitorStatistics.getBucketsVisited() + 1);
            } catch (std::exception& e) {
                std::ostringstream ost;
                ost << "Visitor fail to run completedBucket() notification: "
                    << e.what();
                reportProblem(ost.str());
            }
            delete *it;
            it = _bucketStates.erase(it);
            continue;
        }
        auto cmd = std::make_shared<GetIterCommand>(
                bucketState.getBucket(), bucketState.getIteratorId(), _docBlockSize);
        cmd->setLoadType(_initiatingCmd->getLoadType());
        cmd->getTrace().setLevel(_traceLevel);
        cmd->setPriority(_priority);
        _messageHandler->send(cmd, *this);
        ++bucketState._pendingIterators;
        _bucketStates.erase(it);
        _bucketStates.push_back(&bucketState);
        LOG(debug, "Requested new iterator for visitor '%s'.", _id.c_str());
        return true;
    }

    // If there aren't anymore buckets to iterate, we're done
    if (_bucketStates.size() == 0 && _currentBucket >= _buckets.size()) {
        LOG(debug, "No more buckets to visit for visitor '%s'.", _id.c_str());
        return false;
    }

    // If all current buckets have request state and we're below maxParallel
    // and below maxPending
    // start iterating a new bucket
    uint32_t sentCount = 0;
    while (_bucketStates.size() < _visitorOptions._maxParallel &&
           _bucketStates.size() < _visitorOptions._maxPending &&
           _currentBucket < _buckets.size())
    {
        document::Bucket bucket(_bucketSpace, _buckets[_currentBucket]);
        std::unique_ptr<BucketIterationState> newBucketState(
                new BucketIterationState(*this, *_messageHandler, bucket));
        LOG(debug, "Visitor '%s': Sending create iterator for bucket %s.",
                   _id.c_str(), bucket.getBucketId().toString().c_str());

        spi::Selection selection
            = spi::Selection(spi::DocumentSelection(_documentSelectionString));
        selection.setFromTimestamp(
                spi::Timestamp(_visitorOptions._fromTime.getTime()));
        selection.setToTimestamp(
                spi::Timestamp(_visitorOptions._toTime.getTime()));

        std::shared_ptr<CreateIteratorCommand> cmd(
                new CreateIteratorCommand(bucket,
                                          selection,
                                          _visitorOptions._fieldSet,
                                          _visitorOptions._visitRemoves ?
                                          spi::NEWEST_DOCUMENT_OR_REMOVE :
                                          spi::NEWEST_DOCUMENT_ONLY));

        cmd->setLoadType(_initiatingCmd->getLoadType());
        cmd->getTrace().setLevel(_traceLevel);
        cmd->setPriority(0);
        cmd->setReadConsistency(getRequiredReadConsistency());
        _bucketStates.push_back(newBucketState.release());
        _messageHandler->send(cmd, *this);
        ++_currentBucket;
        ++sentCount;
    }
    if (sentCount == 0) {
        if (LOG_WOULD_LOG(debug)) {
            LOG(debug, "Enough iterators being processed. Doing nothing for "
                "visitor '%s' bucketStates = %d.",
                _id.c_str(), (int)_bucketStates.size());
            for (std::list<BucketIterationState*>::iterator it(
                         _bucketStates.begin());
                 it != _bucketStates.end(); ++it)
            {
                LOG(debug, "Existing: %s", (*it)->toString().c_str());
            }
        }
    }
    return true;
}

} // storage
