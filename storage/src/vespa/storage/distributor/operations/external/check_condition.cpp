// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "check_condition.h"
#include "getoperation.h"
#include "intermediate_message_sender.h"
#include <vespa/document/fieldset/fieldsets.h>
#include <vespa/storage/distributor/distributor_bucket_space.h>
#include <vespa/storage/distributor/distributor_node_context.h>
#include <vespa/storage/distributor/distributor_stripe_operation_context.h>
#include <vespa/storage/distributor/node_supported_features_repo.h>
#include <vespa/vdslib/state/clusterstate.h>
#include <vespa/storageapi/message/persistence.h>
#include <cassert>

#include <vespa/log/log.h>
LOG_SETUP(".distributor.operations.external.check_condition");

namespace storage::distributor {

CheckCondition::Outcome::Outcome(api::ReturnCode error_code, vespalib::Trace trace) noexcept
    : _error_code(std::move(error_code)),
      _result(Result::HasError),
      _trace(std::move(trace))
{
}

CheckCondition::Outcome::Outcome(Result result, vespalib::Trace trace) noexcept
    : _error_code(),
      _result(result),
      _trace(std::move(trace))
{
}

CheckCondition::Outcome::Outcome(Result result) noexcept
    : _error_code(),
      _result(result),
      _trace()
{
}

CheckCondition::Outcome::~Outcome() = default;

CheckCondition::CheckCondition(Outcome known_outcome,
                               const DistributorBucketSpace& bucket_space,
                               const DistributorNodeContext& node_ctx,
                               private_ctor_tag)
    : _doc_id_bucket(),
      _bucket_space(bucket_space),
      _node_ctx(node_ctx),
      _cluster_state_version_at_creation_time(_bucket_space.getClusterState().getVersion()),
      _cond_get_op(),
      _sent_message_map(),
      _outcome(known_outcome)
{
}

CheckCondition::CheckCondition(const document::Bucket& bucket,
                               const document::DocumentId& doc_id,
                               const documentapi::TestAndSetCondition& tas_condition,
                               const DistributorBucketSpace& bucket_space,
                               const DistributorNodeContext& node_ctx,
                               PersistenceOperationMetricSet& condition_probe_metrics,
                               uint32_t trace_level,
                               private_ctor_tag)
    : _doc_id_bucket(bucket),
      _bucket_space(bucket_space),
      _node_ctx(node_ctx),
      _cluster_state_version_at_creation_time(_bucket_space.getClusterState().getVersion()),
      _cond_get_op(),
      _sent_message_map(),
      _outcome()
{
    // Condition checks only return metadata back to the distributor and thus have an empty fieldset.
    // Side note: the BucketId provided to the GetCommand is ignored; GetOperation computes explicitly from the doc ID.
    auto get_cmd = std::make_shared<api::GetCommand>(_doc_id_bucket, doc_id, document::NoFields::NAME);
    get_cmd->set_condition(tas_condition);
    get_cmd->getTrace().setLevel(trace_level);
    _cond_get_op = std::make_shared<GetOperation>(_node_ctx, _bucket_space,
                                                  _bucket_space.getBucketDatabase().acquire_read_guard(),
                                                  std::move(get_cmd), condition_probe_metrics,
                                                  api::InternalReadConsistency::Strong);
}

CheckCondition::~CheckCondition() = default;

void CheckCondition::start_and_send(DistributorStripeMessageSender& sender) {
    IntermediateMessageSender proxy_sender(_sent_message_map, _cond_get_op, sender);
    _cond_get_op->start(proxy_sender);
    if (proxy_sender._reply) {
        // Could not send any Get ops at all; bail out immediately
        handle_internal_get_operation_reply(std::move(proxy_sender._reply));
    }
}

void
CheckCondition::handle_reply(DistributorStripeMessageSender& sender,
                             const std::shared_ptr<api::StorageReply>& reply)
{
    auto op = _sent_message_map.pop(reply->getMsgId());
    assert(op == _cond_get_op); // We only wrap a single operation
    IntermediateMessageSender proxy_sender(_sent_message_map, _cond_get_op, sender);
    _cond_get_op->onReceive(proxy_sender, reply);
    if (proxy_sender._reply) {
        handle_internal_get_operation_reply(std::move(proxy_sender._reply));
    }
}

void CheckCondition::cancel(DistributorStripeMessageSender& sender) {
    IntermediateMessageSender proxy_sender(_sent_message_map, _cond_get_op, sender);
    _cond_get_op->onClose(proxy_sender);
    // We don't propagate any generated reply from the GetOperation, as its existence
    // is an implementation detail.
}

// FIXME this is a (logic-inverted) duplicate of TwoPhaseUpdateOperation and partially of
//  GetOperation, but all can be removed entirely once we redesign how operations are aborted
//  across cluster state edges...!
bool CheckCondition::replica_set_changed_after_get_operation() const {
    auto entries = get_bucket_database_entries(_bucket_space, _doc_id_bucket.getBucketId());

    std::vector<std::pair<document::BucketId, uint16_t>> replicas_in_db_now;
    for (const auto & e : entries) {
        for (uint32_t i = 0; i < e->getNodeCount(); i++) {
            const auto& copy = e->getNodeRef(i);
            replicas_in_db_now.emplace_back(e.getBucketId(), copy.getNode());
        }
    }
    return (replicas_in_db_now != _cond_get_op->replicas_in_db());
}

bool CheckCondition::distributor_no_longer_owns_bucket() const {
    return !_bucket_space.check_ownership_in_pending_and_current_state(_doc_id_bucket.getBucketId()).isOwned();
}

CheckCondition::Outcome::Result
CheckCondition::newest_replica_to_outcome(const std::optional<NewestReplica>& newest) noexcept {
    if (!newest) {
        // Did not find any replicas to send to; implicitly Not Found
        return Outcome::Result::NotFound;
    }
    if (newest->condition_matched) {
        return Outcome::Result::MatchedCondition;
    } else if (newest->is_tombstone || newest->timestamp == 0) {
        return Outcome::Result::NotFound;
    } else {
        return Outcome::Result::DidNotMatchCondition;
    }
}

std::vector<BucketDatabase::Entry>
CheckCondition::get_bucket_database_entries(const DistributorBucketSpace& bucket_space,
                                            const document::BucketId& bucket_id)
{
    std::vector<BucketDatabase::Entry> entries;
    bucket_space.getBucketDatabase().getParents(bucket_id, entries);
    return entries;
}

void CheckCondition::handle_internal_get_operation_reply(std::shared_ptr<api::StorageReply> reply) {
    if (reply->getResult().success()) {
        if (_cond_get_op->any_replicas_failed()) {
            _outcome.emplace(api::ReturnCode(api::ReturnCode::ABORTED,
                                             "One or more replicas failed during test-and-set condition evaluation"),
                             reply->steal_trace());
            return;
        }
        auto state_version_now = _bucket_space.getClusterState().getVersion();
        if (_bucket_space.has_pending_cluster_state()) {
            state_version_now = _bucket_space.get_pending_cluster_state().getVersion();
        }
        if ((state_version_now != _cluster_state_version_at_creation_time)
            && (replica_set_changed_after_get_operation()
                || distributor_no_longer_owns_bucket()))
        {
            // BUCKET_NOT_FOUND is semantically (usually) inaccurate here, but it's what we use for this purpose
            // in existing operations. Checking the replica set will implicitly check for ownership changes,
            // as it will be empty if the distributor no longer owns the bucket.
            //  FIXME but it doesn't handle ABA-cases, so we still want to redesign operation aborting to be
            //    explicitly edge-handled...!
            _outcome.emplace(api::ReturnCode(api::ReturnCode::BUCKET_NOT_FOUND,
                                             "Bucket ownership or replica set changed between condition "
                                             "read and operation write phases"),
                             reply->steal_trace());
        } else {
            auto maybe_newest = _cond_get_op->newest_replica();
            _outcome.emplace(newest_replica_to_outcome(maybe_newest), reply->steal_trace());
        }
    } else {
        _outcome.emplace(reply->getResult(), reply->steal_trace());
    }
}

bool CheckCondition::bucket_has_consistent_replicas(std::span<const BucketDatabase::Entry> entries) {
    // Fast path iff bucket exists AND is consistent (split and copies). Same as TwoPhaseUpdateOperation.
    // TODO consolidate logic
    if (entries.size() != 1) {
        return false;
    }
    return entries[0]->validAndConsistent();
}
bool
CheckCondition::all_nodes_support_document_condition_probe(std::span<const BucketDatabase::Entry> entries,
                                                           const DistributorStripeOperationContext& op_ctx)
{
    // TODO move node set feature checking to repo itself
    const auto& features_repo = op_ctx.node_supported_features_repo();
    for (const auto& entry : entries) {
        for (uint32_t i = 0; i < entry->getNodeCount(); ++i) {
            if (!features_repo.node_supported_features(entry->getNodeRef(i).getNode()).document_condition_probe) {
                return false;
            }
        }
    }

    return true;
}

std::shared_ptr<CheckCondition>
CheckCondition::create_not_found(const DistributorBucketSpace& bucket_space,
                                 const DistributorNodeContext& node_ctx)
{
    return std::make_shared<CheckCondition>(Outcome(Outcome::Result::NotFound),
                                            bucket_space, node_ctx, private_ctor_tag{});
}

std::shared_ptr<CheckCondition>
CheckCondition::create_if_inconsistent_replicas(const document::Bucket& bucket,
                                                const DistributorBucketSpace& bucket_space,
                                                const document::DocumentId& doc_id,
                                                const documentapi::TestAndSetCondition& tas_condition,
                                                const DistributorNodeContext& node_ctx,
                                                const DistributorStripeOperationContext& op_ctx,
                                                PersistenceOperationMetricSet& condition_probe_metrics,
                                                uint32_t trace_level)
{
    // TODO move this check to the caller?
    if (!op_ctx.distributor_config().enable_condition_probing()) {
        return {};
    }
    auto entries = get_bucket_database_entries(bucket_space, bucket.getBucketId());
    if (entries.empty()) {
        return {}; // Not found
    }
    if (bucket_has_consistent_replicas(entries)) {
        return {}; // Replicas are consistent; no need for write-repair
    }
    if (!all_nodes_support_document_condition_probe(entries, op_ctx)) {
        return {}; // Want write-repair, but one or more nodes are too old to use the feature
    }
    return std::make_shared<CheckCondition>(bucket, doc_id, tas_condition, bucket_space, node_ctx,
                                            condition_probe_metrics, trace_level, private_ctor_tag{});
}

}
