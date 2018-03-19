// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @class storage::FileStorManager
 * @ingroup filestorage
 *
 * @version $Id$
 */

#pragma once

#include "filestorhandler.h"
#include "filestormetrics.h"
#include <vespa/vespalib/util/document_runnable.h>
#include <vespa/vespalib/util/sync.h>
#include <vespa/document/bucket/bucketid.h>
#include <vespa/persistence/spi/persistenceprovider.h>
#include <vespa/persistence/spi/metricpersistenceprovider.h>
#include <vespa/storage/bucketdb/storbucketdb.h>
#include <vespa/storage/common/messagesender.h>
#include <vespa/storage/common/servicelayercomponent.h>
#include <vespa/storage/common/statusmessages.h>
#include <vespa/storage/common/storagelinkqueued.h>
#include <vespa/config-stor-filestor.h>
#include <vespa/storage/persistence/diskthread.h>

#include <vespa/storage/persistence/provider_error_wrapper.h>
#include <vespa/storage/common/nodestateupdater.h>
#include <vespa/storageframework/generic/status/htmlstatusreporter.h>

#include <vespa/config/subscription/configuri.h>
#include <vespa/config/helper/ifetchercallback.h>
#include <vespa/config/config.h>

namespace storage {
namespace api {
    class ReturnCode;
    class StorageReply;
}

class BucketMergeTest;
class DiskInfo;
class FileStorManagerTest;
class ReadBucketList;
class ModifiedBucketCheckerThread;
class BucketOwnershipNotifier;
class AbortBucketOperationsCommand;

class FileStorManager : public StorageLinkQueued,
                        public framework::HtmlStatusReporter,
                        public StateListener,
                        private config::IFetcherCallback<vespa::config::content::StorFilestorConfig>,
                        private MessageSender
{
    ServiceLayerComponentRegister& _compReg;
    ServiceLayerComponent _component;
    const spi::PartitionStateList& _partitions;
    spi::PersistenceProvider& _providerCore;
    ProviderErrorWrapper _providerErrorWrapper;
    bool _nodeUpInLastNodeStateSeenByProvider;
    spi::MetricPersistenceProvider::UP _providerMetric;
    spi::PersistenceProvider* _provider;
    
    const document::BucketIdFactory& _bucketIdFactory;
    config::ConfigUri _configUri;

    typedef std::vector<DiskThread::SP> DiskThreads;
    std::vector<DiskThreads> _disks;
    std::unique_ptr<BucketOwnershipNotifier> _bucketOwnershipNotifier;

    std::unique_ptr<vespa::config::content::StorFilestorConfig> _config;
    config::ConfigFetcher _configFetcher;
    uint32_t _threadLockCheckInterval; // In seconds
    bool _failDiskOnError;
    int _killSignal;
    std::shared_ptr<FileStorMetrics> _metrics;
    std::unique_ptr<FileStorHandler> _filestorHandler;
    lib::ClusterState _lastState;

    struct ReplyHolder {
        int refCount;
        std::unique_ptr<api::StorageReply> reply;

        ReplyHolder(int rc, std::unique_ptr<api::StorageReply> r)
            : refCount(rc), reply(std::move(r)) {};
    };

    std::map<api::StorageMessage::Id,
             std::shared_ptr<ReplyHolder> > _splitMessages;
    vespalib::Lock _splitLock;
    mutable vespalib::Monitor _threadMonitor; // Notify to stop sleeping
    bool _closed;

    FileStorManager(const FileStorManager &);
    FileStorManager& operator=(const FileStorManager &);

    std::vector<DiskThreads> getThreads() { return _disks; }

    friend class BucketMergeTest;
    friend class FileStorManagerTest;
    friend class MessageTest;

public:
    explicit FileStorManager(const config::ConfigUri &,
                             const spi::PartitionStateList&,
                             spi::PersistenceProvider&,
                             ServiceLayerComponentRegister&);
    ~FileStorManager();

    void print(std::ostream& out, bool verbose, const std::string& indent) const override;

    // Return true if we are currently merging the given bucket.
    bool isMerging(const document::Bucket& bucket) const;

    FileStorHandler& getFileStorHandler() {
        return *_filestorHandler;
    };

    spi::PersistenceProvider& getPersistenceProvider() {
        return *_provider;
    }
    ProviderErrorWrapper& error_wrapper() noexcept {
        return _providerErrorWrapper;
    }

    void handleNewState() override;

private:
    void configure(std::unique_ptr<vespa::config::content::StorFilestorConfig> config) override;

    void replyWithBucketNotFound(api::StorageMessage&, const document::Bucket&);

    void replyDroppedOperation(api::StorageMessage& msg,
                               const document::Bucket& bucket,
                               api::ReturnCode::Result returnCode,
                               vespalib::stringref reason);

    StorBucketDatabase::WrappedEntry ensureConsistentBucket(
            const document::Bucket& bucket,
            api::StorageMessage& msg,
            const char* callerId);

    bool validateApplyDiffCommandBucket(api::StorageMessage& msg, const StorBucketDatabase::WrappedEntry&);
    bool validateDiffReplyBucket(const StorBucketDatabase::WrappedEntry&, const document::Bucket&);

    StorBucketDatabase::WrappedEntry mapOperationToDisk(api::StorageMessage&, const document::Bucket&);
    StorBucketDatabase::WrappedEntry mapOperationToBucketAndDisk(api::BucketCommand&, const document::DocumentId*);
    bool handlePersistenceMessage(const std::shared_ptr<api::StorageMessage>&, uint16_t disk);

    // Document operations
    bool onPut(const std::shared_ptr<api::PutCommand>&) override;
    bool onUpdate(const std::shared_ptr<api::UpdateCommand>&) override;
    bool onGet(const std::shared_ptr<api::GetCommand>&) override;
    bool onRemove(const std::shared_ptr<api::RemoveCommand>&) override;
    bool onRevert(const std::shared_ptr<api::RevertCommand>&) override;
    bool onBatchPutRemove(const std::shared_ptr<api::BatchPutRemoveCommand>&) override;
    bool onStatBucket(const std::shared_ptr<api::StatBucketCommand>&) override;

    // Bucket operations
    bool onRemoveLocation(const std::shared_ptr<api::RemoveLocationCommand>&) override;
    bool onCreateBucket(const std::shared_ptr<api::CreateBucketCommand>&) override;
    bool onDeleteBucket(const std::shared_ptr<api::DeleteBucketCommand>&) override;
    bool onMergeBucket(const std::shared_ptr<api::MergeBucketCommand>&) override;
    bool onGetBucketDiff(const std::shared_ptr<api::GetBucketDiffCommand>&) override;
    bool onGetBucketDiffReply(const std::shared_ptr<api::GetBucketDiffReply>&) override;
    bool onApplyBucketDiff(const std::shared_ptr<api::ApplyBucketDiffCommand>&) override;
    bool onApplyBucketDiffReply(const std::shared_ptr<api::ApplyBucketDiffReply>&) override;
    bool onJoinBuckets(const std::shared_ptr<api::JoinBucketsCommand>&) override;
    bool onSplitBucket(const std::shared_ptr<api::SplitBucketCommand>&) override;
    bool onSetBucketState(const std::shared_ptr<api::SetBucketStateCommand>&) override;
    bool onNotifyBucketChangeReply(const std::shared_ptr<api::NotifyBucketChangeReply>&) override { return true; }

    // Other
    bool onInternal(const std::shared_ptr<api::InternalCommand>&) override;
    bool onInternalReply(const std::shared_ptr<api::InternalReply>&) override;

    void handleAbortBucketOperations(const std::shared_ptr<AbortBucketOperationsCommand>&);
    void sendCommand(const std::shared_ptr<api::StorageCommand>&) override;
    void sendReply(const std::shared_ptr<api::StorageReply>&) override;
    void sendUp(const std::shared_ptr<api::StorageMessage>&) override;
    void onClose() override;
    void onFlush(bool downwards) override;
    void reportHtmlStatus(std::ostream&, const framework::HttpUrlPath&) const override;
    void storageDistributionChanged() override;
    void updateState();
    void propagateClusterStates();
};

} // storage
