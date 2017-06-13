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

#include <vespa/persistence/spi/persistenceprovider.h>
#include <vespa/storage/persistence/filestorage/mergestatus.h>
#include <vespa/storage/persistence/persistenceutil.h>
#include <vespa/storageapi/message/bucket.h>
#include <vespa/storage/common/messagesender.h>

namespace storage {

class MergeHandler : public Types {

public:
    enum StateFlag {
        IN_USE                     = 0x01,
        DELETED                    = 0x02,
        DELETED_IN_PLACE           = 0x04
    };

    MergeHandler(spi::PersistenceProvider& spi, PersistenceUtil&);
    /** Used for unit testing */
    MergeHandler(spi::PersistenceProvider& spi,
                 PersistenceUtil& env,
                 uint32_t maxChunkSize);

    bool buildBucketInfoList(
            const spi::Bucket& bucket,
            const documentapi::LoadType&,
            Timestamp maxTimestamp,
            uint8_t myNodeIndex,
            std::vector<api::GetBucketDiffCommand::Entry>& output,
            spi::Context& context);
    void fetchLocalData(const spi::Bucket& bucket,
                        const documentapi::LoadType&,
                        std::vector<api::ApplyBucketDiffCommand::Entry>& diff,
                        uint8_t nodeIndex,
                        spi::Context& context);
    api::BucketInfo applyDiffLocally(
                          const spi::Bucket& bucket,
                          const documentapi::LoadType&,
                          std::vector<api::ApplyBucketDiffCommand::Entry>& diff,
                          uint8_t nodeIndex,
                          spi::Context& context);

    MessageTracker::UP handleMergeBucket(api::MergeBucketCommand&,
                                         spi::Context&);
    MessageTracker::UP handleGetBucketDiff(api::GetBucketDiffCommand&,
                                           spi::Context&);
    void handleGetBucketDiffReply(api::GetBucketDiffReply&, MessageSender&);
    MessageTracker::UP handleApplyBucketDiff(api::ApplyBucketDiffCommand&,
                                             spi::Context&);
    void handleApplyBucketDiffReply(api::ApplyBucketDiffReply&, MessageSender&);

private:
    spi::PersistenceProvider& _spi;
    PersistenceUtil& _env;
    uint32_t _maxChunkSize;

    /** Returns a reply if merge is complete */
    api::StorageReply::SP processBucketMerge(const spi::Bucket& bucket,
                                             MergeStatus& status,
                                             MessageSender& sender,
                                             spi::Context& context);

    /**
     * Invoke either put, remove or unrevertable remove on the SPI
     * depending on the flags in the diff entry.
     */
    void applyDiffEntry(const spi::Bucket&,
                        const api::ApplyBucketDiffCommand::Entry&,
                        spi::Context& context,
                        const document::DocumentTypeRepo& repo);

    /**
     * Fill entries-vector with metadata for bucket up to maxTimestamp,
     * sorted ascendingly on entry timestamp.
     * Throws std::runtime_error upon iteration failure.
     */
    void populateMetaData(const spi::Bucket&,
                          Timestamp maxTimestamp,
                          std::vector<spi::DocEntry::UP>& entries,
                          spi::Context& context);

    Document::UP deserializeDiffDocument(
            const api::ApplyBucketDiffCommand::Entry& e,
            const document::DocumentTypeRepo& repo) const;
};

} // storage

