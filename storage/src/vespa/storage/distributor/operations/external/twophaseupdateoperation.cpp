// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "getoperation.h"
#include "putoperation.h"
#include "twophaseupdateoperation.h"
#include "updateoperation.h"
#include <vespa/document/datatype/documenttype.h>
#include <vespa/document/fieldset/fieldsets.h>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/document/select/parser.h>
#include <vespa/storage/distributor/distributor_bucket_space.h>
#include <vespa/storage/distributor/distributor_bucket_space_repo.h>
#include <vespa/storageapi/message/persistence.h>
#include <vespa/vdslib/state/cluster_state_bundle.h>
#include <cinttypes>

#include <vespa/log/log.h>
LOG_SETUP(".distributor.callback.twophaseupdate");

using namespace std::literals::string_literals;
using document::BucketSpace;

namespace storage::distributor {

TwoPhaseUpdateOperation::TwoPhaseUpdateOperation(
        const DistributorNodeContext& node_ctx,
        DistributorStripeOperationContext& op_ctx,
        const DocumentSelectionParser& parser,
        DistributorBucketSpace& bucketSpace,
        std::shared_ptr<api::UpdateCommand> msg,
        DistributorMetricSet& metrics,
        SequencingHandle sequencingHandle)
    : SequencedOperation(std::move(sequencingHandle)),
      _updateMetric(metrics.updates),
      _putMetric(metrics.update_puts),
      _getMetric(metrics.update_gets),
      _metadata_get_metrics(metrics.update_metadata_gets),
      _updateCmd(std::move(msg)),
      _updateReply(),
      _node_ctx(node_ctx),
      _op_ctx(op_ctx),
      _parser(parser),
      _bucketSpace(bucketSpace),
      _sendState(SendState::NONE_SENT),
      _mode(Mode::FAST_PATH),
      _trace(_updateCmd->getTrace().getLevel()),
      _single_get_latency_timer(),
      _fast_path_repair_source_node(0xffff),
      _use_initial_cheap_metadata_fetch_phase(
            _op_ctx.distributor_config().enable_metadata_only_fetch_phase_for_inconsistent_updates()),
      _replySent(false)
{
    document::BucketIdFactory idFactory;
    _updateDocBucketId = idFactory.getBucketId(_updateCmd->getDocumentId());
}

TwoPhaseUpdateOperation::~TwoPhaseUpdateOperation() = default;

namespace {

struct IntermediateMessageSender : DistributorStripeMessageSender {
    SentMessageMap& msgMap;
    std::shared_ptr<Operation> callback;
    DistributorStripeMessageSender& forward;
    std::shared_ptr<api::StorageReply> _reply;

    IntermediateMessageSender(SentMessageMap& mm, std::shared_ptr<Operation> cb, DistributorStripeMessageSender & fwd);
    ~IntermediateMessageSender() override;

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

    const ClusterContext & cluster_context() const override {
        return forward.cluster_context();
    }

    PendingMessageTracker& getPendingMessageTracker() override {
        return forward.getPendingMessageTracker();
    }

    const PendingMessageTracker& getPendingMessageTracker() const override {
        return forward.getPendingMessageTracker();
    }

    const OperationSequencer& operation_sequencer() const noexcept override {
        return forward.operation_sequencer();
    }

    OperationSequencer& operation_sequencer() noexcept override {
        return forward.operation_sequencer();
    }
};

IntermediateMessageSender::IntermediateMessageSender(SentMessageMap& mm,
                                                     std::shared_ptr<Operation> cb,
                                                     DistributorStripeMessageSender & fwd)
    : msgMap(mm),
      callback(std::move(cb)),
      forward(fwd)
{ }

IntermediateMessageSender::~IntermediateMessageSender() = default;

}

const char*
TwoPhaseUpdateOperation::stateToString(SendState state) noexcept
{
    switch (state) {
    case SendState::NONE_SENT:          return "NONE_SENT";
    case SendState::UPDATES_SENT:       return "UPDATES_SENT";
    case SendState::METADATA_GETS_SENT: return "METADATA_GETS_SENT";
    case SendState::SINGLE_GET_SENT:    return "SINGLE_GET_SENT";
    case SendState::FULL_GETS_SENT:     return "FULL_GETS_SENT";
    case SendState::PUTS_SENT:          return "PUTS_SENT";
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
    if (!_updateReply) {
        _updateReply = std::dynamic_pointer_cast<api::UpdateReply>(std::shared_ptr<api::StorageReply>(_updateCmd->makeReply()));
        assert(_updateReply);
    }
}

void
TwoPhaseUpdateOperation::sendReply(
        DistributorStripeMessageSender& sender,
        std::shared_ptr<api::UpdateReply> reply)
{
    assert(!_replySent);
    reply->getTrace().addChild(std::move(_trace));
    sender.sendReply(reply);
    _replySent = true;
}

// This particular method is called when we synthesize our own UpdateReply,
// not when we take over an already produced one from an UpdateOperation.
// The latter will already increment _updateMetric fields implicitly.
void
TwoPhaseUpdateOperation::sendReplyWithResult(
        DistributorStripeMessageSender& sender,
        const api::ReturnCode& result)
{
    ensureUpdateReplyCreated();
    // Don't bump metrics if document not found but otherwise OK.
    // Already counted in metrics prior to calling this method.
    if (!(result.success() && (_updateReply->getOldTimestamp() == 0))) {
        _updateMetric.updateFromResult(result);
    } // else: `notfound` metric already incremented.
    _updateReply->setResult(result);
    sendReply(sender, _updateReply);
}

std::vector<BucketDatabase::Entry>
TwoPhaseUpdateOperation::get_bucket_database_entries() const
{
    std::vector<BucketDatabase::Entry> entries;
    _bucketSpace.getBucketDatabase().getParents(_updateDocBucketId, entries);
    return entries;
}

bool
TwoPhaseUpdateOperation::isFastPathPossible(const std::vector<BucketDatabase::Entry>& entries) const
{
    // Fast path iff bucket exists AND is consistent (split and copies).
    if (entries.size() != 1) {
        return false;
    }
    return entries[0]->validAndConsistent();
}

void
TwoPhaseUpdateOperation::startFastPathUpdate(DistributorStripeMessageSender& sender, std::vector<BucketDatabase::Entry> entries)
{
    _mode = Mode::FAST_PATH;
    LOG(debug, "Update(%s) fast path: sending Update commands", update_doc_id().c_str());
    auto updateOperation = std::make_shared<UpdateOperation>
            (_node_ctx, _op_ctx, _bucketSpace, _updateCmd, std::move(entries), _updateMetric);
    UpdateOperation & op = *updateOperation;
    IntermediateMessageSender intermediate(_sentMessageMap, std::move(updateOperation), sender);
    op.start(intermediate, _node_ctx.clock().getSystemTime());
    transitionTo(SendState::UPDATES_SENT);

    if (intermediate._reply.get()) {
        sendReply(sender, std::dynamic_pointer_cast<api::UpdateReply>(intermediate._reply));
    }
}

void
TwoPhaseUpdateOperation::startSafePathUpdate(DistributorStripeMessageSender& sender)
{
    if (_op_ctx.cluster_state_bundle().block_feed_in_cluster()) {
        send_feed_blocked_error_reply(sender);
        return;
    }
    _mode = Mode::SLOW_PATH;
    auto get_operation = create_initial_safe_path_get_operation();
    GetOperation& op = *get_operation;
    IntermediateMessageSender intermediate(_sentMessageMap, std::move(get_operation), sender);
    _replicas_at_get_send_time = op.replicas_in_db(); // Populated at construction time, not at start()-time
    op.start(intermediate, _node_ctx.clock().getSystemTime());

    transitionTo(_use_initial_cheap_metadata_fetch_phase
                 ? SendState::METADATA_GETS_SENT
                 : SendState::FULL_GETS_SENT);

    if (intermediate._reply.get()) {
        assert(intermediate._reply->getType() == api::MessageType::GET_REPLY);
        // We always trigger the safe path Get reply handling here regardless of whether
        // metadata-only or full Gets were sent. This is because we might get an early
        // reply due to there being no replicas in existence at all for the target bucket.
        // In this case, we rely on the safe path fallback to implicitly create the bucket
        // by performing the update locally and sending CreateBucket+Put to the ideal nodes.
        handleSafePathReceivedGet(sender, static_cast<api::GetReply&>(*intermediate._reply));
    }
}

std::shared_ptr<GetOperation>
TwoPhaseUpdateOperation::create_initial_safe_path_get_operation() {
    document::Bucket bucket(_updateCmd->getBucket().getBucketSpace(), document::BucketId(0));
    const char* field_set = _use_initial_cheap_metadata_fetch_phase ? document::NoFields::NAME : document::AllFields::NAME;
    auto get = std::make_shared<api::GetCommand>(bucket, _updateCmd->getDocumentId(), field_set);
    copyMessageSettings(*_updateCmd, *get);
    // Metadata-only Gets just look at the data in the meta-store, not any fields.
    // The meta-store is always updated before any ACK is returned for a mutation,
    // so all the information we need is guaranteed to be consistent even with a
    // weak read. But since weak reads allow the Get operation to bypass commit
    // queues, latency may be greatly reduced in contended situations.
    auto read_consistency = (_use_initial_cheap_metadata_fetch_phase
                             ? api::InternalReadConsistency::Weak
                             : api::InternalReadConsistency::Strong);
    LOG(debug, "Update(%s) safe path: sending Get commands with field set '%s' "
               "and internal read consistency %s",
               update_doc_id().c_str(), field_set, api::to_string(read_consistency));
    auto& get_metric = (_use_initial_cheap_metadata_fetch_phase ? _metadata_get_metrics : _getMetric);
    return std::make_shared<GetOperation>(
            _node_ctx, _bucketSpace, _bucketSpace.getBucketDatabase().acquire_read_guard(),
            get, get_metric, read_consistency);
}

void
TwoPhaseUpdateOperation::onStart(DistributorStripeMessageSender& sender) {
    auto entries = get_bucket_database_entries();
    if (isFastPathPossible(entries)) {
        startFastPathUpdate(sender, std::move(entries));
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
    auto &bucket_space(_op_ctx.bucket_space_repo().get(_updateCmd->getBucket().getBucketSpace()));
    BucketOwnership bo(bucket_space.check_ownership_in_pending_and_current_state(_updateDocBucketId));
    return !bo.isOwned();
}

void
TwoPhaseUpdateOperation::sendLostOwnershipTransientErrorReply(DistributorStripeMessageSender& sender)
{
    sendReplyWithResult(sender,
            api::ReturnCode(api::ReturnCode::BUCKET_NOT_FOUND,
                            "Distributor lost ownership of bucket between "
                            "executing the read and write phases of a two-"
                            "phase update operation"));
}

void
TwoPhaseUpdateOperation::send_feed_blocked_error_reply(DistributorStripeMessageSender& sender)
{
    sendReplyWithResult(sender,
                        api::ReturnCode(api::ReturnCode::NO_SPACE,
                                        "External feed is blocked due to resource exhaustion: " +
                                        _op_ctx.cluster_state_bundle().feed_block()->description()));
}

void
TwoPhaseUpdateOperation::schedulePutsWithUpdatedDocument(std::shared_ptr<document::Document> doc,
                                                         api::Timestamp putTimestamp, DistributorStripeMessageSender& sender)
{
    if (lostBucketOwnershipBetweenPhases()) {
        sendLostOwnershipTransientErrorReply(sender);
        return;
    }
    document::Bucket bucket(_updateCmd->getBucket().getBucketSpace(), document::BucketId(0));
    auto put = std::make_shared<api::PutCommand>(bucket, doc, putTimestamp);
    copyMessageSettings(*_updateCmd, *put);
    auto putOperation = std::make_shared<PutOperation>(_node_ctx, _op_ctx, _bucketSpace, std::move(put), _putMetric);
    PutOperation & op = *putOperation;
    IntermediateMessageSender intermediate(_sentMessageMap, std::move(putOperation), sender);
    op.start(intermediate, _node_ctx.clock().getSystemTime());
    transitionTo(SendState::PUTS_SENT);

    LOG(debug, "Update(%s): sending Puts at timestamp %" PRIu64, update_doc_id().c_str(), putTimestamp);
    LOG(spam,  "Update(%s): Put document is: %s", update_doc_id().c_str(), doc->toString(true).c_str());

    if (intermediate._reply.get()) {
        sendReplyWithResult(sender, intermediate._reply->getResult());
    }
}

void
TwoPhaseUpdateOperation::onReceive(DistributorStripeMessageSender& sender, const std::shared_ptr<api::StorageReply>& msg)
{
    if (_mode == Mode::FAST_PATH) {
        handleFastPathReceive(sender, msg);
    } else {
        handleSafePathReceive(sender, msg);
    }
}

void
TwoPhaseUpdateOperation::handleFastPathReceive(DistributorStripeMessageSender& sender,
                                               const std::shared_ptr<api::StorageReply>& msg)
{
    if (msg->getType() == api::MessageType::GET_REPLY) {
        assert(_sendState == SendState::FULL_GETS_SENT);
        auto& getReply = static_cast<api::GetReply&>(*msg);
        addTraceFromReply(getReply);

        LOG(debug, "Update(%s) fast path: Get reply had result %s",
            update_doc_id().c_str(), getReply.getResult().toString().c_str());

        if (!getReply.getResult().success()) {
            sendReplyWithResult(sender, getReply.getResult());
            return;
        }

        if (!getReply.getDocument().get()) {
            // Weird, document is no longer there ... Just fail.
            sendReplyWithResult(sender, api::ReturnCode(api::ReturnCode::INTERNAL_FAILURE, ""));
            return;
        }
        schedulePutsWithUpdatedDocument(getReply.getDocument(), _op_ctx.generate_unique_timestamp(), sender);
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
            auto& cb = dynamic_cast<UpdateOperation&>(callbackOp);

            std::pair<document::BucketId, uint16_t> bestNode = cb.getNewestTimestampLocation();
            auto intermediate_update_reply = std::dynamic_pointer_cast<api::UpdateReply>(intermediate._reply);
            assert(intermediate_update_reply);

            if (!intermediate_update_reply->getResult().success() ||
                bestNode.first == document::BucketId(0))
            {
                if (intermediate_update_reply->getResult().success() &&
                    (intermediate_update_reply->getOldTimestamp() == 0))
                {
                    _updateMetric.failures.notfound.inc();
                }
                // Failed or was consistent
                sendReply(sender, std::move(intermediate_update_reply));
            } else {
                LOG(debug, "Update(%s) fast path: was inconsistent!", update_doc_id().c_str());

                _updateReply = std::move(intermediate_update_reply);
                _fast_path_repair_source_node = bestNode.second;
                document::Bucket bucket(_updateCmd->getBucket().getBucketSpace(), bestNode.first);
                auto cmd = std::make_shared<api::GetCommand>(bucket, _updateCmd->getDocumentId(), document::AllFields::NAME);
                copyMessageSettings(*_updateCmd, *cmd);

                sender.sendToNode(lib::NodeType::STORAGE, _fast_path_repair_source_node, cmd);
                transitionTo(SendState::FULL_GETS_SENT);
            }
        }
    } else {
        if (intermediate._reply.get()) {
            // PUTs are done.
            addTraceFromReply(*intermediate._reply);
            sendReplyWithResult(sender, intermediate._reply->getResult());
            LOG(warning, "Forced convergence of '%s' using document from node %u",
                update_doc_id().c_str(), _fast_path_repair_source_node);
        }
    }
}

void
TwoPhaseUpdateOperation::handleSafePathReceive(DistributorStripeMessageSender& sender,
                                               const std::shared_ptr<api::StorageReply>& msg)
{
    // No explicit operation is associated with the direct replica Get operation,
    // so we handle its reply separately.
    if (_sendState == SendState::SINGLE_GET_SENT) {
        assert(msg->getType() == api::MessageType::GET_REPLY);
        handle_safe_path_received_single_full_get(sender, dynamic_cast<api::GetReply&>(*msg));
        return;
    }
    std::shared_ptr<Operation> callback = _sentMessageMap.pop(msg->getMsgId());
    assert(callback.get());
    Operation & callbackOp = *callback;

    IntermediateMessageSender intermediate(_sentMessageMap, std::move(callback), sender);
    callbackOp.receive(intermediate, msg);

    if (!intermediate._reply.get()) {
        return; // Not enough replies received yet or we're draining callbacks.
    }
    addTraceFromReply(*intermediate._reply);
    if (_sendState == SendState::METADATA_GETS_SENT) {
        assert(intermediate._reply->getType() == api::MessageType::GET_REPLY);
        const auto& get_op = dynamic_cast<const GetOperation&>(*intermediate.callback);
        handle_safe_path_received_metadata_get(sender, static_cast<api::GetReply&>(*intermediate._reply),
                                               get_op.newest_replica(), get_op.any_replicas_failed());
    } else if (_sendState == SendState::FULL_GETS_SENT) {
        assert(intermediate._reply->getType() == api::MessageType::GET_REPLY);
        handleSafePathReceivedGet(sender, static_cast<api::GetReply&>(*intermediate._reply));
    } else if (_sendState == SendState::PUTS_SENT) {
        assert(intermediate._reply->getType() == api::MessageType::PUT_REPLY);
        handleSafePathReceivedPut(sender, static_cast<api::PutReply&>(*intermediate._reply));
    } else {
        assert(!"Unknown state");
    }
}

void TwoPhaseUpdateOperation::handle_safe_path_received_single_full_get(
        DistributorStripeMessageSender& sender,
        api::GetReply& reply)
{
    LOG(spam, "Received single full Get reply for '%s'", update_doc_id().c_str());
    if (_replySent) {
        return; // Bail out; the operation has been concurrently closed.
    }
    addTraceFromReply(reply);
    if (reply.getResult().success()) {
        _getMetric.ok.inc();
    } else {
        _getMetric.failures.storagefailure.inc();
    }
    assert(_single_get_latency_timer.has_value());
    _getMetric.latency.addValue(_single_get_latency_timer->getElapsedTimeAsDouble());
    handleSafePathReceivedGet(sender, reply);
}

void TwoPhaseUpdateOperation::handle_safe_path_received_metadata_get(
        DistributorStripeMessageSender& sender, api::GetReply& reply,
        const std::optional<NewestReplica>& newest_replica,
        bool any_replicas_failed)
{
    LOG(debug, "Update(%s): got (metadata only) Get reply with result %s",
        update_doc_id().c_str(), reply.getResult().toString().c_str());

    if (!reply.getResult().success()) {
        sendReplyWithResult(sender, reply.getResult());
        return;
    }
    // It's possible for a single replica to fail during processing without the entire
    // Get operation failing. Although we know a priori if replicas are out of sync,
    // we don't know which one has the highest timestamp (it might have been the one
    // on the node that the metadata Get just failed towards). To err on the side of
    // caution we abort the update if this happens. If a simple metadata Get fails, it
    // is highly likely that a full partial update or put operation would fail as well.
    if (any_replicas_failed) {
        LOG(debug, "Update(%s): had failed replicas, aborting update", update_doc_id().c_str());
        sendReplyWithResult(sender, api::ReturnCode(api::ReturnCode::Result::ABORTED,
                            "One or more metadata Get operations failed; aborting Update"));
        return;
    }
    if (!replica_set_unchanged_after_get_operation()) {
        // Use BUCKET_NOT_FOUND to trigger a silent retry.
        LOG(debug, "Update(%s): replica set has changed after metadata get phase", update_doc_id().c_str());
        sendReplyWithResult(sender, api::ReturnCode(api::ReturnCode::Result::BUCKET_NOT_FOUND,
                                                    "Replica sets changed between update phases, client must retry"));
        return;
    }
    if (reply.had_consistent_replicas()) {
        LOG(debug, "Update(%s): metadata Gets consistent; restarting in fast path", update_doc_id().c_str());
        restart_with_fast_path_due_to_consistent_get_timestamps(sender);
        return;
    }
    // If we've gotten here, we must have had no Get failures and replicas must
    // be somehow inconsistent. Replicas can only be inconsistent if their timestamps
    // mismatch, so we must have observed at least one non-zero timestamp.
    assert(newest_replica.has_value() && (newest_replica->timestamp != api::Timestamp(0)));
    // Timestamps were not in sync, so we have to fetch the document from the highest
    // timestamped replica, apply the update to it and then explicitly Put the result
    // to all replicas.
    // Note that this timestamp may be for a tombstone (remove) entry, in which case
    // conditional create-if-missing behavior kicks in as usual.
    // TODO avoid sending the Get at all if the newest replica is marked as a tombstone.
    _single_get_latency_timer.emplace(_node_ctx.clock());
    document::Bucket bucket(_updateCmd->getBucket().getBucketSpace(), newest_replica->bucket_id);
    LOG(debug, "Update(%s): sending single payload Get to %s on node %u (had timestamp %" PRIu64 ")",
        update_doc_id().c_str(), bucket.toString().c_str(),
        newest_replica->node, newest_replica->timestamp);
    auto cmd = std::make_shared<api::GetCommand>(bucket, _updateCmd->getDocumentId(), document::AllFields::NAME);
    copyMessageSettings(*_updateCmd, *cmd);
    sender.sendToNode(lib::NodeType::STORAGE, newest_replica->node, cmd);

    transitionTo(SendState::SINGLE_GET_SENT);
}

void
TwoPhaseUpdateOperation::handleSafePathReceivedGet(DistributorStripeMessageSender& sender, api::GetReply& reply)
{
    LOG(debug, "Update(%s): got Get reply with code %s",
        _updateCmd->getDocumentId().toString().c_str(),
        reply.getResult().toString().c_str());

    if (!reply.getResult().success()) {
        sendReplyWithResult(sender, reply.getResult());
        return;
    }
    // Single Get could technically be considered consistent with itself, so make
    // sure we never treat that as sufficient for restarting in the fast path.
    if ((_sendState != SendState::SINGLE_GET_SENT) && may_restart_with_fast_path(reply)) {
        restart_with_fast_path_due_to_consistent_get_timestamps(sender);
        return;
    }

    document::Document::SP docToUpdate;
    api::Timestamp putTimestamp = _op_ctx.generate_unique_timestamp();

    if (reply.getDocument()) {
        api::Timestamp receivedTimestamp = reply.getLastModifiedTimestamp();
        if (!satisfiesUpdateTimestampConstraint(receivedTimestamp)) {
            _updateMetric.failures.notfound.inc();
            sendReplyWithResult(sender, api::ReturnCode(api::ReturnCode::OK,
                                                        "No document with requested timestamp found"));
            return;
        }
        if (!processAndMatchTasCondition(sender, *reply.getDocument())) {
            return; // Reply already generated at this point.
        }
        docToUpdate = reply.getDocument();
        setUpdatedForTimestamp(receivedTimestamp);
    } else if (hasTasCondition() && !shouldCreateIfNonExistent()) {
        replyWithTasFailure(sender, "Document did not exist");
        return;
    } else if (shouldCreateIfNonExistent()) {
        LOG(debug, "No existing documents found for %s, creating blank document to update",
            update_doc_id().c_str());
        docToUpdate = createBlankDocument();
        setUpdatedForTimestamp(putTimestamp);
    } else {
        _updateMetric.failures.notfound.inc();
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

bool TwoPhaseUpdateOperation::may_restart_with_fast_path(const api::GetReply& reply) {
    return (_op_ctx.distributor_config().update_fast_path_restart_enabled() &&
            !_replicas_at_get_send_time.empty() && // To ensure we send CreateBucket+Put if no replicas exist.
            reply.had_consistent_replicas() &&
            replica_set_unchanged_after_get_operation());
}

bool TwoPhaseUpdateOperation::replica_set_unchanged_after_get_operation() const {
    std::vector<BucketDatabase::Entry> entries;
    _bucketSpace.getBucketDatabase().getParents(_updateDocBucketId, entries);

    std::vector<std::pair<document::BucketId, uint16_t>> replicas_in_db_now;
    for (const auto & e : entries) {
        for (uint32_t i = 0; i < e->getNodeCount(); i++) {
            const auto& copy = e->getNodeRef(i);
            replicas_in_db_now.emplace_back(e.getBucketId(), copy.getNode());
        }
    }
    return (replicas_in_db_now == _replicas_at_get_send_time);
}

void TwoPhaseUpdateOperation::restart_with_fast_path_due_to_consistent_get_timestamps(DistributorStripeMessageSender& sender) {
    LOG(debug, "Update(%s): all Gets returned in initial safe path were consistent, restarting in fast path mode",
               update_doc_id().c_str());
    if (lostBucketOwnershipBetweenPhases()) {
        sendLostOwnershipTransientErrorReply(sender);
        return;
    }
    _updateMetric.fast_path_restarts.inc();
    // Must not be any other messages in flight, or we might mis-interpret them when we
    // have switched back to fast-path mode.
    assert(_sentMessageMap.empty());
    startFastPathUpdate(sender, {});
}

bool
TwoPhaseUpdateOperation::processAndMatchTasCondition(DistributorStripeMessageSender& sender,
                                                     const document::Document& candidateDoc)
{
    if (!hasTasCondition()) {
        return true; // No condition; nothing to do here.
    }

    std::unique_ptr<document::select::Node> selection;
    try {
         selection = _parser.parse_selection(_updateCmd->getCondition().getSelection());
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
TwoPhaseUpdateOperation::replyWithTasFailure(DistributorStripeMessageSender& sender, vespalib::stringref message)
{
    sendReplyWithResult(sender, api::ReturnCode(api::ReturnCode::TEST_AND_SET_CONDITION_FAILED, message));
}

void
TwoPhaseUpdateOperation::setUpdatedForTimestamp(api::Timestamp ts)
{
    ensureUpdateReplyCreated();
    _updateReply->setOldTimestamp(ts);
}

std::shared_ptr<document::Document>
TwoPhaseUpdateOperation::createBlankDocument() const
{
    const document::DocumentUpdate& up(*_updateCmd->getUpdate());
    auto doc = std::make_shared<document::Document>(*up.getRepoPtr(), up.getType(), up.getId());
    return doc;
}

void
TwoPhaseUpdateOperation::handleSafePathReceivedPut(DistributorStripeMessageSender& sender, const api::PutReply& reply)
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
TwoPhaseUpdateOperation::addTraceFromReply(api::StorageReply & reply)
{
    _trace.addChild(reply.steal_trace());
}

void
TwoPhaseUpdateOperation::onClose(DistributorStripeMessageSender& sender) {
    while (true) {
        std::shared_ptr<Operation> cb = _sentMessageMap.pop();

        if (cb) {
            IntermediateMessageSender intermediate(_sentMessageMap, std::shared_ptr<Operation>(), sender);
            cb->onClose(intermediate);
            // We will _only_ forward UpdateReply instances up, since those
            // are created by UpdateOperation and are bound to the original
            // UpdateCommand. Any other intermediate replies will be replies
            // to synthetic commands created for gets/puts and should never be
            // propagated to the outside world.
            auto candidateReply = std::move(intermediate._reply);
            if (candidateReply && candidateReply->getType() == api::MessageType::UPDATE_REPLY) {
                assert(_mode == Mode::FAST_PATH);
                sendReply(sender, std::dynamic_pointer_cast<api::UpdateReply>(candidateReply)); // Sets _replySent
            }
        } else {
            break;
        }
    }

    if (!_replySent) {
        sendReplyWithResult(sender, api::ReturnCode(api::ReturnCode::ABORTED));
    }
}

vespalib::string TwoPhaseUpdateOperation::update_doc_id() const {
    assert(_updateCmd.get() != nullptr);
    return _updateCmd->getDocumentId().toString();
}

}
