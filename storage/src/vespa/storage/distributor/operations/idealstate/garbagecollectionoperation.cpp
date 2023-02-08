// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "garbagecollectionoperation.h"
#include <vespa/storage/distributor/idealstatemanager.h>
#include <vespa/storage/distributor/idealstatemetricsset.h>
#include <vespa/storage/distributor/top_level_distributor.h>
#include <vespa/storage/distributor/distributor_bucket_space.h>
#include <vespa/storage/distributor/node_supported_features_repo.h>
#include <vespa/storageapi/message/removelocation.h>
#include <vespa/vespalib/stllike/hash_map.hpp>
#include <algorithm>

#include <vespa/log/log.h>
LOG_SETUP(".distributor.operation.idealstate.gc");

namespace storage::distributor {

GarbageCollectionOperation::GarbageCollectionOperation(const ClusterContext& cluster_ctx, const BucketAndNodes& nodes)
    : IdealStateOperation(nodes),
      _tracker(cluster_ctx),
      _phase(Phase::NotStarted),
      _cluster_state_version_at_phase1_start_time(0),
      _remove_candidates(),
      _replica_info(),
      _max_documents_removed(0),
      _is_done(false)
{}

GarbageCollectionOperation::~GarbageCollectionOperation() = default;

const char* GarbageCollectionOperation::to_string(Phase phase) noexcept {
    switch (phase) {
    case Phase::NotStarted:        return "NotStarted";
    case Phase::LegacySinglePhase: return "LegacySinglePhase";
    case Phase::ReadMetadataPhase: return "ReadMetadataPhase";
    case Phase::WriteRemovesPhase: return "WriteRemovesPhase";
    }
    abort();
}

bool GarbageCollectionOperation::all_involved_nodes_support_two_phase_gc() const noexcept {
    const auto& features_repo = _manager->operation_context().node_supported_features_repo();
    for (uint16_t node : getNodes()) {
        if (!features_repo.node_supported_features(node).two_phase_remove_location) {
            return false;
        }
    }
    return true;
}

std::vector<spi::IdAndTimestamp> GarbageCollectionOperation::compile_phase_two_send_set() const {
    std::vector<spi::IdAndTimestamp> docs_to_remove;
    docs_to_remove.reserve(_remove_candidates.size());
    for (const auto& cand : _remove_candidates) {
        docs_to_remove.emplace_back(cand.first, cand.second);
    }
    // Use timestamp order to provide test determinism and allow for backend linear merging (if needed).
    // Tie-break on GID upon collisions (which technically should never happen...!)
    auto ts_then_gid_order = [](const spi::IdAndTimestamp& lhs, const spi::IdAndTimestamp& rhs) noexcept {
        if (lhs.timestamp != rhs.timestamp) {
            return (lhs.timestamp < rhs.timestamp);
        }
        return (lhs.id.getGlobalId() < rhs.id.getGlobalId());
    };
    std::sort(docs_to_remove.begin(), docs_to_remove.end(), ts_then_gid_order);
    return docs_to_remove;
}

void GarbageCollectionOperation::send_current_phase_remove_locations(DistributorStripeMessageSender& sender) {
    BucketDatabase::Entry entry = _bucketSpace->getBucketDatabase().get(getBucketId());
    std::vector<uint16_t> nodes = entry->getNodes();
    auto docs_to_remove = compile_phase_two_send_set(); // Always empty unless in phase 2 of two-phase GC

    for (size_t i = 0; i < nodes.size(); ++i) {
        auto command = std::make_shared<api::RemoveLocationCommand>(
                _manager->operation_context().distributor_config().getGarbageCollectionSelection(),
                getBucket());
        if (_phase == Phase::ReadMetadataPhase) {
            command->set_only_enumerate_docs(true);
        } else if (_phase == Phase::WriteRemovesPhase) {
            if (i < nodes.size() - 1) {
                command->set_explicit_remove_set(docs_to_remove);
            } else {
                command->set_explicit_remove_set(std::move(docs_to_remove));
            }
        } // else: legacy command
        command->setPriority((_phase != Phase::WriteRemovesPhase)
                             ? _priority
                             : _manager->operation_context().distributor_config().default_external_feed_priority());
        _tracker.queueCommand(command, nodes[i]);
    }
    _tracker.flushQueue(sender);
}

void GarbageCollectionOperation::onStart(DistributorStripeMessageSender& sender) {
    if (_manager->operation_context().distributor_config().enable_two_phase_garbage_collection() &&
        all_involved_nodes_support_two_phase_gc())
    {
        _cluster_state_version_at_phase1_start_time = _bucketSpace->getClusterState().getVersion();
        LOG(debug, "Starting first phase of two-phase GC for %s at cluster state version %u",
            getBucket().toString().c_str(), _cluster_state_version_at_phase1_start_time);
        transition_to(Phase::ReadMetadataPhase);
    } else {
        LOG(debug, "Starting legacy single-phase GC for %s", getBucket().toString().c_str());
        transition_to(Phase::LegacySinglePhase);
    }
    send_current_phase_remove_locations(sender);
    if (_tracker.finished()) {
        done();
    }
}

void
GarbageCollectionOperation::onReceive(DistributorStripeMessageSender& sender,
                                      const std::shared_ptr<api::StorageReply>& reply)
{
    auto* rep = dynamic_cast<api::RemoveLocationReply*>(reply.get());
    assert(rep != nullptr);

    uint16_t node = _tracker.handleReply(*rep);

    if (!rep->getResult().failed()) {
        if (_phase == Phase::LegacySinglePhase) {
            handle_ok_legacy_reply(node, *rep);
        } else if (_phase == Phase::ReadMetadataPhase) {
            handle_ok_phase1_reply(*rep);
        } else {
            assert(_phase == Phase::WriteRemovesPhase);
            handle_ok_phase2_reply(node, *rep);
        }
    } else {
        _ok = false;
    }

    if (_tracker.finished()) {
        const bool op_complete = (!_ok || _phase == Phase::LegacySinglePhase || _phase == Phase::WriteRemovesPhase);
        if (_ok) {
            if (op_complete) {
                merge_received_bucket_info_into_db();
            } else {
                assert(_phase == Phase::ReadMetadataPhase);
                on_metadata_read_phase_done(sender);
            }
        }
        if (op_complete) {
            mark_operation_complete();
        }
    }
}

void GarbageCollectionOperation::update_replica_response_info_from_reply(uint16_t from_node, const api::RemoveLocationReply& reply) {
    _replica_info.emplace_back(_manager->operation_context().generate_unique_timestamp(),
                               from_node, reply.getBucketInfo());
    _max_documents_removed = std::max(_max_documents_removed, reply.documents_removed());
}

void GarbageCollectionOperation::handle_ok_legacy_reply(uint16_t from_node, const api::RemoveLocationReply& reply) {
    update_replica_response_info_from_reply(from_node, reply);
}

GarbageCollectionOperation::RemoveCandidates
GarbageCollectionOperation::steal_selection_matches_as_candidates(api::RemoveLocationReply& reply) {
    auto candidates = reply.steal_selection_matches();
    RemoveCandidates as_map;
    as_map.resize(candidates.size());
    for (auto& cand : candidates) {
        as_map.insert(std::make_pair(std::move(cand.id), cand.timestamp));
    }
    return as_map;
}

void GarbageCollectionOperation::handle_ok_phase1_reply(api::RemoveLocationReply& reply) {
    assert(reply.documents_removed() == 0);
    auto their_matches = steal_selection_matches_as_candidates(reply);
    for (auto& new_cand : their_matches) {
        auto& maybe_existing_ts = _remove_candidates[new_cand.first];
        maybe_existing_ts = std::max(new_cand.second, maybe_existing_ts);
    }
}

void GarbageCollectionOperation::handle_ok_phase2_reply(uint16_t from_node, const api::RemoveLocationReply& reply) {
    update_replica_response_info_from_reply(from_node, reply);
}

bool GarbageCollectionOperation::may_start_write_phase() const {
    if (!_ok) {
        return false; // Already broken, no reason to proceed.
    }
    const auto state_version_now = _bucketSpace->getClusterState().getVersion();
    if ((state_version_now != _cluster_state_version_at_phase1_start_time) ||
        _bucketSpace->has_pending_cluster_state())
    {
        LOG(debug, "GC(%s): not sending write phase; cluster state has changed, or a change is pending",
            getBucket().toString().c_str());
        return false;
    }
    // If bucket is gone, or has become inconsistently split, abort mission.
    std::vector<BucketDatabase::Entry> entries;
    _bucketSpace->getBucketDatabase().getAll(getBucketId(), entries);
    if ((entries.size() != 1) || (entries[0].getBucketId() != getBucketId())) {
        LOG(debug, "GC(%s): not sending write phase; bucket has become inconsistent",
            getBucket().toString().c_str());
        return false;
    }
    return true;
}

void GarbageCollectionOperation::on_metadata_read_phase_done(DistributorStripeMessageSender& sender) {
    if (!may_start_write_phase()) {
        _ok = false;
        mark_operation_complete();
        return;
    }
    std::vector<document::DocumentId> already_pending_write;
    for (auto& cand : _remove_candidates) {
        auto maybe_seq_token = sender.operation_sequencer().try_acquire(getBucket().getBucketSpace(), cand.first);
        if (maybe_seq_token.valid()) {
            _gc_write_locks.emplace_back(std::move(maybe_seq_token));
            LOG(spam, "GC(%s): acquired write lock for '%s'; adding to GC set",
                getBucket().toString().c_str(), cand.first.toString().c_str());
        } else {
            already_pending_write.emplace_back(cand.first);
            LOG(spam, "GC(%s): failed to acquire write lock for '%s'; not including in GC set",
                getBucket().toString().c_str(), cand.first.toString().c_str());
        }
    }
    for (auto& rm_entry : already_pending_write) {
        _remove_candidates.erase(rm_entry);
    }
    if (_remove_candidates.empty()) {
        update_last_gc_timestamp_in_db(); // Nothing to remove now, try again later.
        mark_operation_complete();
        return;
    }
    LOG(debug, "GC(%s): Sending phase 2 GC with %zu entries (with acquired write locks). "
               "%zu documents had pending writes and could not be GCd at this time",
        getBucket().toString().c_str(), _remove_candidates.size(), already_pending_write.size());
    transition_to(Phase::WriteRemovesPhase);
    send_current_phase_remove_locations(sender);
}

void GarbageCollectionOperation::update_last_gc_timestamp_in_db() {
    BucketDatabase::Entry dbentry = _bucketSpace->getBucketDatabase().get(getBucketId());
    if (dbentry.valid()) {
        dbentry->setLastGarbageCollectionTime(vespalib::count_s(_manager->node_context().clock().getSystemTime().time_since_epoch()));
        LOG(debug, "GC(%s): Tagging bucket completed at time %u",
            getBucket().toString().c_str(), dbentry->getLastGarbageCollectionTime());
        _bucketSpace->getBucketDatabase().update(dbentry);
    }
}

void GarbageCollectionOperation::merge_received_bucket_info_into_db() {
    // TODO avoid two separate DB ops for this. Current API currently does not make this elegant.
    _manager->operation_context().update_bucket_database(getBucket(), _replica_info);
    update_last_gc_timestamp_in_db();
}

void GarbageCollectionOperation::update_gc_metrics() {
    auto metric_base = _manager->getMetrics().operations[IdealStateOperation::GARBAGE_COLLECTION];
    auto gc_metrics = std::dynamic_pointer_cast<GcMetricSet>(metric_base);
    assert(gc_metrics);
    gc_metrics->documents_removed.inc(_max_documents_removed);
}

void GarbageCollectionOperation::mark_operation_complete() {
    assert(!_is_done);
    if (_ok) {
        update_gc_metrics();
    }
    done();
    _is_done = true;
}

void GarbageCollectionOperation::transition_to(Phase new_phase) {
    LOG(spam, "GC(%s): state transition %s -> %s",
        getBucket().toString().c_str(), to_string(_phase), to_string(new_phase));
    _phase = new_phase;
}

bool
GarbageCollectionOperation::shouldBlockThisOperation(uint32_t, uint16_t, uint8_t) const {
    return true;
}

}
