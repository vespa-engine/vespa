// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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

#include "types.h"
#include <vespa/persistence/spi/bucket.h>
#include <vespa/persistence/spi/docentry.h>
#include <vespa/storageapi/message/bucket.h>
#include <vespa/storage/common/cluster_context.h>
#include <vespa/storage/common/messagesender.h>

namespace storage {

namespace spi {
    struct PersistenceProvider;
    class Context;
}
class PersistenceUtil;
class ApplyBucketDiffEntryResult;
class MergeStatus;

class MergeHandler : public Types {

public:
    enum StateFlag {
        IN_USE                     = 0x01,
        DELETED                    = 0x02,
        DELETED_IN_PLACE           = 0x04
    };

    MergeHandler(PersistenceUtil& env, spi::PersistenceProvider& spi,
                 const ClusterContext& cluster_context, const framework::Clock & clock,
                 uint32_t maxChunkSize = 4190208,
                 uint32_t commonMergeChainOptimalizationMinimumSize = 64);

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
    api::BucketInfo applyDiffLocally(
                          const spi::Bucket& bucket,
                          std::vector<api::ApplyBucketDiffCommand::Entry>& diff,
                          uint8_t nodeIndex,
                          spi::Context& context) const;

    MessageTrackerUP handleMergeBucket(api::MergeBucketCommand&, MessageTrackerUP) const;
    MessageTrackerUP handleGetBucketDiff(api::GetBucketDiffCommand&, MessageTrackerUP) const;
    void handleGetBucketDiffReply(api::GetBucketDiffReply&, MessageSender&) const;
    MessageTrackerUP handleApplyBucketDiff(api::ApplyBucketDiffCommand&, MessageTrackerUP) const;
    void handleApplyBucketDiffReply(api::ApplyBucketDiffReply&, MessageSender&) const;

private:
    const framework::Clock   &_clock;
    const ClusterContext &_cluster_context;
    PersistenceUtil          &_env;
    spi::PersistenceProvider &_spi;
    const uint32_t            _maxChunkSize;
    const uint32_t            _commonMergeChainOptimalizationMinimumSize;

    /** Returns a reply if merge is complete */
    api::StorageReply::SP processBucketMerge(const spi::Bucket& bucket,
                                             MergeStatus& status,
                                             MessageSender& sender,
                                             spi::Context& context) const;

    /**
     * Invoke either put, remove or unrevertable remove on the SPI
     * depending on the flags in the diff entry.
     */
    ApplyBucketDiffEntryResult applyDiffEntry(const spi::Bucket&,
                                              const api::ApplyBucketDiffCommand::Entry&,
                                              spi::Context& context,
                                              const document::DocumentTypeRepo& repo) const;

    /**
     * Fill entries-vector with metadata for bucket up to maxTimestamp,
     * sorted ascendingly on entry timestamp.
     * Throws std::runtime_error upon iteration failure.
     */
    void populateMetaData(const spi::Bucket&,
                          Timestamp maxTimestamp,
                          std::vector<spi::DocEntry::UP>& entries,
                          spi::Context& context) const;

    Document::UP deserializeDiffDocument(
            const api::ApplyBucketDiffCommand::Entry& e,
            const document::DocumentTypeRepo& repo) const;
};

} // storage

