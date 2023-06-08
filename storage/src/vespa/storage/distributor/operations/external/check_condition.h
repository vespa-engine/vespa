// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "newest_replica.h"
#include <vespa/document/bucket/bucket.h>
#include <vespa/storage/bucketdb/bucketdatabase.h>
#include <vespa/storage/distributor/sentmessagemap.h>
#include <vespa/storageapi/messageapi/returncode.h>
#include <memory>
#include <optional>
#include <span>
#include <vector>

namespace document { class DocumentId; }
namespace documentapi { class TestAndSetCondition; }
namespace storage::api { class StorageReply; }

namespace storage::distributor {

class DistributorBucketSpace;
class DistributorNodeContext;
class DistributorStripeMessageSender;
class DistributorStripeOperationContext;
class GetOperation;
class PersistenceOperationMetricSet;

/**
 * Encapsulates all logic required to evaluate a test-and-set condition for a
 * particular document ID across a set of bucket replicas.
 *
 * Usage and lifecycle:
 *
 * 1. Invoke start_and_send() once.
 * 2. Check if maybe_outcome() indicates that a result is ready. If so, the
 *    instance can be safely discarded after the outcome has been handled.
 * 3. Wait for responses to messages sent.
 * 4. When a GetReply is received, invoke handle_reply() with it. Note that
 *    this may transparently send new requests in case of transient failure
 *    responses.
 * 5. Check if maybe_outcome() is ready, c.f. #2.
 * 6. Go to 3.
 *
 * Although this class appears very similar to an Operation in that it sends
 * requests and accepts responses, it is not an actual Operation itself.
 * It is instead intended to be directly used _by_ other phased operations that
 * require distributed condition checking as part of their write repair logic.
 * One major distinction between a CheckCondition and an Operation is that this
 * class does _not_ generate a Reply to communicate its result. Instead, the
 * caller must check maybe_outcome() after every operation on the instance to
 * poll if the condition evaluation is done (or has failed).
 */
class CheckCondition {
public:
    class Outcome {
    public:
        enum class Result {
            HasError,
            MatchedCondition,
            DidNotMatchCondition,
            NotFound,
        };

        Outcome(api::ReturnCode error_code, vespalib::Trace trace) noexcept;
        Outcome(Result result, vespalib::Trace trace) noexcept;
        explicit Outcome(Result result) noexcept;
        ~Outcome();

        [[nodiscard]] bool failed() const noexcept {
            return _error_code.failed();
        }

        const api::ReturnCode& error_code() const noexcept {
            return _error_code;
        }

        [[nodiscard]] bool matched_condition() const noexcept {
            return _result == Result::MatchedCondition;
        }

        [[nodiscard]] bool not_found() const noexcept {
            return _result == Result::NotFound;
        }

        [[nodiscard]] const vespalib::Trace& trace() const noexcept {
            return _trace;
        }

        [[nodiscard]] vespalib::Trace&& steal_trace() noexcept {
            return std::move(_trace);
        }

    private:
        api::ReturnCode _error_code;
        Result          _result;
        vespalib::Trace _trace;
    };
private:
    const document::Bucket        _doc_id_bucket;
    const DistributorBucketSpace& _bucket_space;
    const DistributorNodeContext& _node_ctx;
    const uint32_t                _cluster_state_version_at_creation_time; // TODO encapsulate this better
    std::shared_ptr<GetOperation> _cond_get_op;
    SentMessageMap                _sent_message_map;
    std::optional<Outcome>        _outcome;

    struct private_ctor_tag {};
public:
    CheckCondition(Outcome known_outcome,
                   const DistributorBucketSpace& bucket_space,
                   const DistributorNodeContext& node_ctx,
                   private_ctor_tag);
    CheckCondition(const document::Bucket& bucket,
                   const document::DocumentId& doc_id,
                   const documentapi::TestAndSetCondition& tas_condition,
                   const DistributorBucketSpace& bucket_space,
                   const DistributorNodeContext& node_ctx,
                   PersistenceOperationMetricSet& condition_probe_metrics,
                   uint32_t trace_level,
                   private_ctor_tag);
    ~CheckCondition();

    void start_and_send(DistributorStripeMessageSender& sender);
    void handle_reply(DistributorStripeMessageSender& sender,
                      const std::shared_ptr<api::StorageReply>& reply);
    void cancel(DistributorStripeMessageSender& sender);

    [[nodiscard]] std::optional<Outcome>& maybe_outcome() noexcept {
        return _outcome;
    }

    [[nodiscard]] static std::shared_ptr<CheckCondition> create_if_inconsistent_replicas(
            const document::Bucket& bucket,
            const DistributorBucketSpace& bucket_space,
            const document::DocumentId& doc_id,
            const documentapi::TestAndSetCondition& tas_condition,
            const DistributorNodeContext& node_ctx,
            const DistributorStripeOperationContext& op_ctx,
            PersistenceOperationMetricSet& condition_probe_metrics,
            uint32_t trace_level);
private:
    [[nodiscard]] bool replica_set_changed_after_get_operation() const;
    [[nodiscard]] bool distributor_no_longer_owns_bucket() const;

    void handle_internal_get_operation_reply(std::shared_ptr<api::StorageReply> reply);

    [[nodiscard]] static Outcome::Result newest_replica_to_outcome(
            const std::optional<NewestReplica>& newest) noexcept;

    [[nodiscard]] static bool bucket_has_consistent_replicas(
            std::span<const BucketDatabase::Entry> entries);

    [[nodiscard]] static bool all_nodes_support_document_condition_probe(
            std::span<const BucketDatabase::Entry> entries,
            const DistributorStripeOperationContext& op_ctx);

    [[nodiscard]] static std::vector<BucketDatabase::Entry> get_bucket_database_entries(
            const DistributorBucketSpace& bucket_space,
            const document::BucketId& bucket_id);

    [[nodiscard]] static std::shared_ptr<CheckCondition> create_not_found(
            const DistributorBucketSpace& bucket_space,
            const DistributorNodeContext& node_ctx);
};

}
