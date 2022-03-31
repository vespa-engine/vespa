// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @class storage::MergeHandler
 *
 * @brief Handles a merge of a single bucket.
 *
 * A merge is a complex operation in many stages covering multiple nodes. It
 * needs to track some state of ongoing merges, and it also needs quite a bit
 * of logic.
 *
 * This class implements tracks the state and implements the logic, such that
 * the rest of the provider layer does not need to concern itself with merges.
 */
#pragma once

#include "merge_bucket_info_syncer.h"
#include <vespa/persistence/spi/bucket.h>
#include <vespa/storageapi/message/bucket.h>
#include <vespa/storage/common/cluster_context.h>
#include <vespa/storage/common/messagesender.h>
#include <vespa/vespalib/util/monitored_refcount.h>
#include <vespa/storageframework/generic/clock/time.h>
#include <atomic>

namespace vespalib { class ISequencedTaskExecutor; }
namespace document { class Document; }
namespace storage {

namespace spi {
    struct PersistenceProvider;
    class Context;
    class DocEntry;
}
class PersistenceUtil;
class ApplyBucketDiffState;
class MergeStatus;
class MessageTracker;

class MergeHandler : public MergeBucketInfoSyncer {
private:
    using MessageTrackerUP = std::unique_ptr<MessageTracker>;
    using Timestamp = framework::MicroSecTime;
public:
    enum StateFlag {
        IN_USE                     = 0x01,
        DELETED                    = 0x02,
        DELETED_IN_PLACE           = 0x04
    };

    MergeHandler(PersistenceUtil& env, spi::PersistenceProvider& spi,
                 const ClusterContext& cluster_context, const framework::Clock & clock,
                 vespalib::ISequencedTaskExecutor& executor,
                 uint32_t maxChunkSize = 4190208,
                 uint32_t commonMergeChainOptimalizationMinimumSize = 64);

    ~MergeHandler() override;

    bool buildBucketInfoList(
            const spi::Bucket& bucket,
            Timestamp maxTimestamp,
            uint8_t myNodeIndex,
            std::vector<api::GetBucketDiffCommand::Entry>& output,
            spi::Context& context) const;
    void fetchLocalData(const spi::Bucket& bucket,
                        std::vector<api::ApplyBucketDiffCommand::Entry>& diff,
                        uint8_t nodeIndex,
                        spi::Context& context) const;
    void applyDiffLocally(const spi::Bucket& bucket,
                          std::vector<api::ApplyBucketDiffCommand::Entry>& diff,
                          uint8_t nodeIndex,
                          spi::Context& context,
                          std::shared_ptr<ApplyBucketDiffState> async_results) const;
    void sync_bucket_info(const spi::Bucket& bucket) const override;
    void schedule_delayed_delete(std::unique_ptr<ApplyBucketDiffState>) const override;

    MessageTrackerUP handleMergeBucket(api::MergeBucketCommand&, MessageTrackerUP) const;
    MessageTrackerUP handleGetBucketDiff(api::GetBucketDiffCommand&, MessageTrackerUP) const;
    void handleGetBucketDiffReply(api::GetBucketDiffReply&, MessageSender&) const;
    MessageTrackerUP handleApplyBucketDiff(api::ApplyBucketDiffCommand&, MessageTrackerUP) const;
    void handleApplyBucketDiffReply(api::ApplyBucketDiffReply&, MessageSender&, MessageTrackerUP) const;
    void drain_async_writes();

    // Thread safe, as it's set during live reconfig from the main filestor manager.
    void set_throttle_merge_feed_ops(bool throttle) noexcept {
        _throttle_merge_feed_ops.store(throttle, std::memory_order_relaxed);
    }

    [[nodiscard]] bool throttle_merge_feed_ops() const noexcept {
        return _throttle_merge_feed_ops.load(std::memory_order_relaxed);
    }

private:
    using DocEntryList = std::vector<std::unique_ptr<spi::DocEntry>>;
    const framework::Clock   &_clock;
    const ClusterContext     &_cluster_context;
    PersistenceUtil          &_env;
    spi::PersistenceProvider &_spi;
    std::unique_ptr<vespalib::MonitoredRefCount> _monitored_ref_count;
    const uint32_t            _maxChunkSize;
    const uint32_t            _commonMergeChainOptimalizationMinimumSize;
    vespalib::ISequencedTaskExecutor& _executor;
    std::atomic<bool>         _throttle_merge_feed_ops;

    MessageTrackerUP handleGetBucketDiffStage2(api::GetBucketDiffCommand&, MessageTrackerUP) const;
    /** Returns a reply if merge is complete */
    api::StorageReply::SP processBucketMerge(const spi::Bucket& bucket,
                                             MergeStatus& status,
                                             MessageSender& sender,
                                             spi::Context& context,
                                             std::shared_ptr<ApplyBucketDiffState>& async_results) const;

    /**
     * Invoke either put, remove or unrevertable remove on the SPI
     * depending on the flags in the diff entry.
     */
    void applyDiffEntry(std::shared_ptr<ApplyBucketDiffState> async_results,
                        const spi::Bucket&,
                        const api::ApplyBucketDiffCommand::Entry&,
                        const document::DocumentTypeRepo& repo) const;

    /**
     * Fill entries-vector with metadata for bucket up to maxTimestamp,
     * sorted ascendingly on entry timestamp.
     * Throws std::runtime_error upon iteration failure.
     */
    void populateMetaData(const spi::Bucket&,
                          Timestamp maxTimestamp,
                          DocEntryList & entries,
                          spi::Context& context) const;

    std::unique_ptr<document::Document>
    deserializeDiffDocument(const api::ApplyBucketDiffCommand::Entry& e, const document::DocumentTypeRepo& repo) const;
};

} // storage

