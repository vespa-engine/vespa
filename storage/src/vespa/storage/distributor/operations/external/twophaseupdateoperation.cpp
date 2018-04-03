// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "twophaseupdateoperation.h"
#include "getoperation.h"
#include "putoperation.h"
#include "updateoperation.h"
#include <vespa/storage/distributor/distributor_bucket_space.h>
#include <vespa/storageapi/message/persistence.h>
#include <vespa/storageapi/message/batch.h>
#include <vespa/document/datatype/documenttype.h>
#include <vespa/document/select/parser.h>
#include <vespa/vespalib/stllike/hash_map.hpp>

#include <vespa/log/log.h>
LOG_SETUP(".distributor.callback.twophaseupdate");

using namespace std::literals::string_literals;
using document::BucketSpace;

namespace storage::distributor {

TwoPhaseUpdateOperation::TwoPhaseUpdateOperation(
        DistributorComponent& manager,
        DistributorBucketSpace &bucketSpace,
        const std::shared_ptr<api::UpdateCommand>& msg,
        DistributorMetricSet& metrics,
        SequencingHandle sequencingHandle)
    : SequencedOperation(std::move(sequencingHandle)),
    _updateMetric(metrics.updates[msg->getLoadType()]),
    _putMetric(metrics.update_puts[msg->getLoadType()]),
    _getMetric(metrics.update_gets[msg->getLoadType()]),
    _updateCmd(msg),
    _updateReply(),
    _manager(manager),
    _bucketSpace(bucketSpace),
    _sendState(SendState::NONE_SENT),
    _mode(Mode::FAST_PATH),
    _replySent(false)
{
    document::BucketIdFactory idFactory;
    _updateDocBucketId = idFactory.getBucketId(_updateCmd->getDocumentId());
}

TwoPhaseUpdateOperation::~TwoPhaseUpdateOperation() {}

namespace {

struct IntermediateMessageSender : DistributorMessageSender {
    SentMessageMap& msgMap;
    std::shared_ptr<Operation> callback;
    DistributorMessageSender& forward;
    std::shared_ptr<api::StorageReply> _reply;

    IntermediateMessageSender(SentMessageMap& mm, std::shared_ptr<Operation> cb, DistributorMessageSender & fwd);
    ~IntermediateMessageSender();

    void sendCommand(const std::shared_ptr<api::StorageCommand>& cmd) override {
        msgMap.insert(cmd->getMsgId(), callback);
        forward.sendCommand(cmd);
    };

    void sendReply(const std::shared_ptr<api::StorageReply>& reply) override {
        _reply = reply;
    }

    int getDistributorIndex() const override {
        return forward.getDistributorIndex();
    }

    const std::string& getClusterName() const override {
        return forward.getClusterName();
    }

    const PendingMessageTracker& getPendingMessageTracker() const override {
        return forward.getPendingMessageTracker();
    }
};

IntermediateMessageSender::IntermediateMessageSender(SentMessageMap& mm,
                                                     std::shared_ptr<Operation> cb,
                                                     DistributorMessageSender & fwd)
    : msgMap(mm),
      callback(std::move(cb)),
      forward(fwd)
{ }
IntermediateMessageSender::~IntermediateMessageSender() = default;

}

const char*
TwoPhaseUpdateOperation::stateToString(SendState state)
{
    switch (state) {
    case SendState::NONE_SENT: return "NONE_SENT";
    case SendState::UPDATES_SENT: return "UPDATES_SENT";
    case SendState::GETS_SENT: return "GETS_SENT";
    case SendState::PUTS_SENT: return "PUTS_SENT";
    default:
        assert(!"Unknown state");
        return "";
    }
}

void
TwoPhaseUpdateOperation::transitionTo(SendState newState)
{
    assert(newState != SendState::NONE_SENT);
    LOG(spam, "Transitioning operation %p state %s ->  %s",
        this, stateToString(_sendState), stateToString(newState));
    _sendState = newState;
}

void
TwoPhaseUpdateOperation::ensureUpdateReplyCreated()
{
    if (!_updateReply.get()) {
        _updateReply = _updateCmd->makeReply();
    }
}

void
TwoPhaseUpdateOperation::sendReply(
        DistributorMessageSender& sender,
        std::shared_ptr<api::StorageReply>& reply)
{
    assert(!_replySent);
    if (!_trace.isEmpty()) {
        reply->getTrace().getRoot().addChild(_trace);
    }
    sender.sendReply(reply);
    _replySent = true;
}

void
TwoPhaseUpdateOperation::sendReplyWithResult(
        DistributorMessageSender& sender,
        const api::ReturnCode& result)
{
    ensureUpdateReplyCreated();
    _updateReply->setResult(result);
    sendReply(sender, _updateReply);
}

bool
TwoPhaseUpdateOperation::isFastPathPossible() const
{
    // Fast path iff bucket exists AND is consistent (split and copies).
    std::vector<BucketDatabase::Entry> entries;
    _bucketSpace.getBucketDatabase().getParents(_updateDocBucketId, entries);

    if (entries.size() != 1) {
        return false;
    }
    return entries[0]->validAndConsistent();
}

void
TwoPhaseUpdateOperation::startFastPathUpdate(DistributorMessageSender& sender)
{
    _mode = Mode::FAST_PATH;
    auto updateOperation = std::make_shared<UpdateOperation>(_manager, _bucketSpace, _updateCmd, _updateMetric);
    UpdateOperation & op = *updateOperation;
    IntermediateMessageSender intermediate(_sentMessageMap, std::move(updateOperation), sender);
    op.start(intermediate, _manager.getClock().getTimeInMillis());
    transitionTo(SendState::UPDATES_SENT);

    if (intermediate._reply.get()) {
        sendReply(sender, intermediate._reply);
    }
}

void
TwoPhaseUpdateOperation::startSafePathUpdate(DistributorMessageSender& sender)
{
    LOG(debug, "Update(%s) safe path: sending Get commands", _updateCmd->getDocumentId().toString().c_str());

    _mode = Mode::SLOW_PATH;
    document::Bucket bucket(_updateCmd->getBucket().getBucketSpace(), document::BucketId(0));
    auto get = std::make_shared<api::GetCommand>(bucket, _updateCmd->getDocumentId(),"[all]");
    copyMessageSettings(*_updateCmd, *get);
    auto getOperation = std::make_shared<GetOperation>(_manager, _bucketSpace, get, _getMetric);
    GetOperation & op = *getOperation;
    IntermediateMessageSender intermediate(_sentMessageMap, std::move(getOperation), sender);
    op.start(intermediate, _manager.getClock().getTimeInMillis());
    transitionTo(SendState::GETS_SENT);

    if (intermediate._reply.get()) {
        assert(intermediate._reply->getType() == api::MessageType::GET_REPLY);
        handleSafePathReceivedGet(sender, static_cast<api::GetReply&>(*intermediate._reply));
    }
}

void
TwoPhaseUpdateOperation::onStart(DistributorMessageSender& sender) {
    if (isFastPathPossible()) {
        startFastPathUpdate(sender);
    } else {
        startSafePathUpdate(sender);
    }
}

/**
 * Verify that we still own this bucket. We don't want to put this check
 * in the regular PutOperation class since the common case is that such
 * operations are executed after the distributor has synchronously verified
 * the ownership in the current state already. It's only during two phase
 * updates that the ownership may change between the initial check and
 * actually executing a Put for the bucket.
 */
bool
TwoPhaseUpdateOperation::lostBucketOwnershipBetweenPhases() const
{
    document::Bucket updateDocBucket(_updateCmd->getBucket().getBucketSpace(), _updateDocBucketId);
    BucketOwnership bo(_manager.checkOwnershipInPendingAndCurrentState(updateDocBucket));
    return !bo.isOwned();
}

void
TwoPhaseUpdateOperation::sendLostOwnershipTransientErrorReply(DistributorMessageSender& sender)
{
    sendReplyWithResult(sender,
            api::ReturnCode(api::ReturnCode::BUCKET_NOT_FOUND,
                            "Distributor lost ownership of bucket between "
                            "executing the read and write phases of a two-"
                            "phase update operation"));
}

void
TwoPhaseUpdateOperation::schedulePutsWithUpdatedDocument(std::shared_ptr<document::Document> doc,
                                                         api::Timestamp putTimestamp, DistributorMessageSender& sender)
{
    if (lostBucketOwnershipBetweenPhases()) {
        sendLostOwnershipTransientErrorReply(sender);
        return;
    }
    document::Bucket bucket(_updateCmd->getBucket().getBucketSpace(), document::BucketId(0));
    auto put = std::make_shared<api::PutCommand>(bucket, doc, putTimestamp);
    copyMessageSettings(*_updateCmd, *put);
    auto putOperation = std::make_shared<PutOperation>(_manager, _bucketSpace, std::move(put), _putMetric);
    PutOperation & op = *putOperation;
    IntermediateMessageSender intermediate(_sentMessageMap, std::move(putOperation), sender);
    op.start(intermediate, _manager.getClock().getTimeInMillis());
    transitionTo(SendState::PUTS_SENT);

    LOG(debug, "Update(%s): sending Put commands with doc %s",
        _updateCmd->getDocumentId().toString().c_str(), doc->toString(true).c_str());

    if (intermediate._reply.get()) {
        sendReplyWithResult(sender, intermediate._reply->getResult());
    }
}

void
TwoPhaseUpdateOperation::onReceive(DistributorMessageSender& sender, const std::shared_ptr<api::StorageReply>& msg)
{
    if (_mode == Mode::FAST_PATH) {
        handleFastPathReceive(sender, msg);
    } else {
        handleSafePathReceive(sender, msg);
    }
}

void
TwoPhaseUpdateOperation::handleFastPathReceive(DistributorMessageSender& sender,
                                               const std::shared_ptr<api::StorageReply>& msg)
{
    if (msg->getType() == api::MessageType::GET_REPLY) {
        assert(_sendState == SendState::GETS_SENT);
        api::GetReply& getReply = static_cast<api::GetReply&> (*msg);
        addTraceFromReply(getReply);

        LOG(debug, "Update(%s) Get reply had result: %s",
            _updateCmd->getDocumentId().toString().c_str(),
            getReply.getResult().toString().c_str());

        if (!getReply.getResult().success()) {
            sendReplyWithResult(sender, getReply.getResult());
            return;
        }

        if (!getReply.getDocument().get()) {
            // Weird, document is no longer there ... Just fail.
            sendReplyWithResult(sender, api::ReturnCode(api::ReturnCode::INTERNAL_FAILURE, ""));
            return;
        }
        schedulePutsWithUpdatedDocument(getReply.getDocument(), _manager.getUniqueTimestamp(), sender);
        return;
    }

    std::shared_ptr<Operation> callback = _sentMessageMap.pop(msg->getMsgId());
    assert(callback.get());
    Operation & callbackOp = *callback;
    IntermediateMessageSender intermediate(_sentMessageMap, std::move(callback), sender);
    callbackOp.receive(intermediate, msg);

    if (msg->getType() == api::MessageType::UPDATE_REPLY) {
        if (intermediate._reply.get()) {
            assert(_sendState == SendState::UPDATES_SENT);
            addTraceFromReply(*intermediate._reply);
            UpdateOperation& cb = static_cast<UpdateOperation&> (callbackOp);

            std::pair<document::BucketId, uint16_t> bestNode = cb.getNewestTimestampLocation();

            if (!intermediate._reply->getResult().success() ||
                bestNode.first == document::BucketId(0)) {
                // Failed or was consistent
                sendReply(sender, intermediate._reply);
            } else {
                LOG(debug, "Update(%s) fast path: was inconsistent!", _updateCmd->getDocumentId().toString().c_str());

                _updateReply = intermediate._reply;
                document::Bucket bucket(_updateCmd->getBucket().getBucketSpace(), bestNode.first);
                auto cmd = std::make_shared<api::GetCommand>(bucket, _updateCmd->getDocumentId(), "[all]");
                copyMessageSettings(*_updateCmd, *cmd);

                sender.sendToNode(lib::NodeType::STORAGE, bestNode.second, cmd);
                transitionTo(SendState::GETS_SENT);
            }
        }
    } else {
        if (intermediate._reply.get()) {
            // PUTs are done.
            addTraceFromReply(*intermediate._reply);
            sendReplyWithResult(sender, intermediate._reply->getResult());
        }
    }
}

void
TwoPhaseUpdateOperation::handleSafePathReceive(DistributorMessageSender& sender,
                                               const std::shared_ptr<api::StorageReply>& msg)
{
    std::shared_ptr<Operation> callback = _sentMessageMap.pop(msg->getMsgId());
    assert(callback.get());
    Operation & callbackOp = *callback;

    IntermediateMessageSender intermediate(_sentMessageMap, std::move(callback), sender);
    callbackOp.receive(intermediate, msg);

    if (!intermediate._reply.get()) {
        return; // Not enough replies received yet or we're draining callbacks.
    }
    addTraceFromReply(*intermediate._reply);
    if (_sendState == SendState::GETS_SENT) {
        assert(intermediate._reply->getType() == api::MessageType::GET_REPLY);
        handleSafePathReceivedGet(sender, static_cast<api::GetReply&>(*intermediate._reply));
    } else if (_sendState == SendState::PUTS_SENT) {
        assert(intermediate._reply->getType() == api::MessageType::PUT_REPLY);
        handleSafePathReceivedPut(sender, static_cast<api::PutReply&>(*intermediate._reply));
    } else {
        assert(!"Unknown state");
    }
}

void
TwoPhaseUpdateOperation::handleSafePathReceivedGet(DistributorMessageSender& sender, api::GetReply& reply)
{
    LOG(debug, "Update(%s): got Get reply with code %s",
        _updateCmd->getDocumentId().toString().c_str(),
        reply.getResult().toString().c_str());

    if (!reply.getResult().success()) {
        sendReplyWithResult(sender, reply.getResult());
        return;
    }
    document::Document::SP docToUpdate;
    api::Timestamp putTimestamp = _manager.getUniqueTimestamp();

    if (reply.getDocument().get()) {
        api::Timestamp receivedTimestamp = reply.getLastModifiedTimestamp();
        if (!satisfiesUpdateTimestampConstraint(receivedTimestamp)) {
            sendReplyWithResult(sender, api::ReturnCode(api::ReturnCode::OK,
                                                        "No document with requested timestamp found"));
            return;
        }
        if (!processAndMatchTasCondition(sender, *reply.getDocument())) {
            return; // Reply already generated at this point.
        }
        docToUpdate = reply.getDocument();
        setUpdatedForTimestamp(receivedTimestamp);
    } else if (hasTasCondition()) {
        replyWithTasFailure(sender, "Document did not exist");
        return;
    } else if (shouldCreateIfNonExistent()) {
        LOG(debug, "No existing documents found for %s, creating blank document to update",
            _updateCmd->getUpdate()->getId().toString().c_str());
        docToUpdate = createBlankDocument();
        setUpdatedForTimestamp(putTimestamp);
    } else {
        sendReplyWithResult(sender, reply.getResult());
        return;
    }
    try {
        applyUpdateToDocument(*docToUpdate);
        schedulePutsWithUpdatedDocument(docToUpdate, putTimestamp, sender);
    } catch (vespalib::Exception& e) {
        sendReplyWithResult(sender, api::ReturnCode(api::ReturnCode::INTERNAL_FAILURE, e.getMessage()));
    }
}

bool
TwoPhaseUpdateOperation::processAndMatchTasCondition(DistributorMessageSender& sender,
                                                     const document::Document& candidateDoc)
{
    if (!hasTasCondition()) {
        return true; // No condition; nothing to do here.
    }

    document::select::Parser parser(*_manager.getTypeRepo(), _manager.getBucketIdFactory());
    std::unique_ptr<document::select::Node> selection;
    try {
         selection = parser.parse(_updateCmd->getCondition().getSelection());
    } catch (const document::select::ParsingFailedException & e) {
        sendReplyWithResult(sender, api::ReturnCode(
                api::ReturnCode::ILLEGAL_PARAMETERS,
                "Failed to parse test and set condition: "s + e.getMessage()));
        return false;
    }

    if (selection->contains(candidateDoc) != document::select::Result::True) {
        replyWithTasFailure(sender, "Condition did not match document");
        return false;
    }
    return true;
}

bool
TwoPhaseUpdateOperation::hasTasCondition() const noexcept
{
    return _updateCmd->getCondition().isPresent();
}

void
TwoPhaseUpdateOperation::replyWithTasFailure(DistributorMessageSender& sender, vespalib::stringref message)
{
    sendReplyWithResult(sender, api::ReturnCode(api::ReturnCode::TEST_AND_SET_CONDITION_FAILED, message));
}

void
TwoPhaseUpdateOperation::setUpdatedForTimestamp(api::Timestamp ts)
{
    ensureUpdateReplyCreated();
    static_cast<api::UpdateReply&>(*_updateReply).setOldTimestamp(ts);
}

std::shared_ptr<document::Document>
TwoPhaseUpdateOperation::createBlankDocument() const
{
    const document::DocumentUpdate& up(*_updateCmd->getUpdate());
    return std::make_shared<document::Document>(up.getType(), up.getId());
}

void
TwoPhaseUpdateOperation::handleSafePathReceivedPut(DistributorMessageSender& sender, const api::PutReply& reply)
{
    sendReplyWithResult(sender, reply.getResult());
}

void
TwoPhaseUpdateOperation::applyUpdateToDocument(document::Document& doc) const
{
    _updateCmd->getUpdate()->applyTo(doc);
}

bool
TwoPhaseUpdateOperation::shouldCreateIfNonExistent() const
{
    return _updateCmd->getUpdate()->getCreateIfNonExistent();
}

bool
TwoPhaseUpdateOperation::satisfiesUpdateTimestampConstraint(api::Timestamp ts) const
{
    return (_updateCmd->getOldTimestamp() == 0 || _updateCmd->getOldTimestamp() == ts);
}

void
TwoPhaseUpdateOperation::addTraceFromReply(const api::StorageReply& reply)
{
    _trace.addChild(reply.getTrace().getRoot());
}

void
TwoPhaseUpdateOperation::onClose(DistributorMessageSender& sender) {
    while (true) {
        std::shared_ptr<Operation> cb = _sentMessageMap.pop();

        if (cb) {
            IntermediateMessageSender intermediate(_sentMessageMap, std::shared_ptr<Operation > (), sender);
            cb->onClose(intermediate);
            // We will _only_ forward UpdateReply instances up, since those
            // are created by UpdateOperation and are bound to the original
            // UpdateCommand. Any other intermediate replies will be replies
            // to synthetic commands created for gets/puts and should never be
            // propagated to the outside world.
            auto candidateReply = std::move(intermediate._reply);
            if (candidateReply && candidateReply->getType() == api::MessageType::UPDATE_REPLY) {
                assert(_mode == Mode::FAST_PATH);
                sendReply(sender, candidateReply); // Sets _replySent
            }
        } else {
            break;
        }
    }

    if (!_replySent) {
        sendReplyWithResult(sender, api::ReturnCode(api::ReturnCode::ABORTED));
    }
}

}
