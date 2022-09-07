// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "idealstateoperation.h"
#include <vespa/document/base/documentid.h>
#include <vespa/storage/bucketdb/bucketcopy.h>
#include <vespa/storage/distributor/messagetracker.h>
#include <vespa/storage/distributor/operation_sequencer.h>
#include <vespa/persistence/spi/id_and_timestamp.h>
#include <vespa/vespalib/stllike/hash_map.h>
#include <vector>

namespace storage::distributor {

class PendingMessageTracker;

class GarbageCollectionOperation final : public IdealStateOperation {
public:
    GarbageCollectionOperation(const ClusterContext& cluster_ctx,
                               const BucketAndNodes& nodes);
    ~GarbageCollectionOperation() override;

    void onStart(DistributorStripeMessageSender& sender) override;
    void onReceive(DistributorStripeMessageSender& sender, const std::shared_ptr<api::StorageReply> &) override;
    const char* getName() const override { return "garbagecollection"; };
    Type getType() const override { return GARBAGE_COLLECTION; }
    bool shouldBlockThisOperation(uint32_t, uint16_t, uint8_t) const override;
    bool is_two_phase() const noexcept {
        return ((_phase == Phase::ReadMetadataPhase) || (_phase == Phase::WriteRemovesPhase));
    }
    bool is_done() const noexcept { return _is_done; }

protected:
    MessageTracker _tracker;
private:
    enum class Phase {
        NotStarted,
        LegacySinglePhase,
        ReadMetadataPhase,
        WriteRemovesPhase
    };

    static const char* to_string(Phase phase) noexcept;

    struct DocIdHasher {
        size_t operator()(const document::DocumentId& id) const noexcept {
            return document::GlobalId::hash()(id.getGlobalId());
        }
    };
    using RemoveCandidates = vespalib::hash_map<document::DocumentId, spi::Timestamp, DocIdHasher>;

    Phase                         _phase;
    uint32_t                      _cluster_state_version_at_phase1_start_time;
    RemoveCandidates              _remove_candidates;
    std::vector<SequencingHandle> _gc_write_locks;
    std::vector<BucketCopy>       _replica_info;
    uint32_t                      _max_documents_removed;
    bool                          _is_done;

    static RemoveCandidates steal_selection_matches_as_candidates(api::RemoveLocationReply& reply);

    void send_current_phase_remove_locations(DistributorStripeMessageSender& sender);
    std::vector<spi::IdAndTimestamp> compile_phase_two_send_set() const;

    void handle_ok_legacy_reply(uint16_t from_node, const api::RemoveLocationReply& reply);
    void handle_ok_phase1_reply(api::RemoveLocationReply& reply);
    void handle_ok_phase2_reply(uint16_t from_node, const api::RemoveLocationReply& reply);
    void update_replica_response_info_from_reply(uint16_t from_node, const api::RemoveLocationReply& reply);
    void on_metadata_read_phase_done(DistributorStripeMessageSender& sender);
    [[nodiscard]] bool may_start_write_phase() const;
    [[nodiscard]] bool all_involved_nodes_support_two_phase_gc() const noexcept;
    void update_last_gc_timestamp_in_db();
    void merge_received_bucket_info_into_db();
    void update_gc_metrics();
    void mark_operation_complete();
    void transition_to(Phase new_phase);
};

}
