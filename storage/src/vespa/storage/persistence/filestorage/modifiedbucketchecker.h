// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/storage/common/content_bucket_space_repo.h>
#include <vespa/storage/common/storagecomponent.h>
#include <vespa/storage/common/servicelayercomponent.h>
#include <vespa/storage/common/storagelink.h>
#include <vespa/storage/config/config-stor-server.h>
#include <vespa/storage/persistence/messages.h>
#include <vespa/storage/persistence/types.h>
#include <vespa/document/bucket/bucketidlist.h>
#include <vespa/config/helper/ifetchercallback.h>

namespace config {
    class ConfigUri;
    class ConfigFetcher;
}
namespace storage {

namespace spi { struct PersistenceProvider; }

class ModifiedBucketChecker
    : public StorageLink,
      public framework::Runnable,
      public Types,
      private config::IFetcherCallback<
              vespa::config::content::core::StorServerConfig>
{
public:
    typedef std::unique_ptr<ModifiedBucketChecker> UP;

    ModifiedBucketChecker(ServiceLayerComponentRegister& compReg,
                          spi::PersistenceProvider& provide,
                          const config::ConfigUri& configUri);
    ~ModifiedBucketChecker();

    void configure(std::unique_ptr<vespa::config::content::core::StorServerConfig>) override;

    void run(framework::ThreadHandle& thread) override;
    bool tick();
    void onOpen() override;
    void onClose() override;

    void setUnitTestingSingleThreadedMode() {
        _singleThreadMode = true;
    }

private:
    bool onInternalReply(const std::shared_ptr<api::InternalReply>&) override;
    bool currentChunkFinished() const {
        return _pendingRequests == 0;
    }
    bool moreChunksRemaining() const {
        return !_rechecksNotStarted.empty();
    }
    bool requestModifiedBucketsFromProvider(document::BucketSpace bucketSpace);
    void nextRecheckChunk(std::vector<RecheckBucketInfoCommand::SP>&);
    void dispatchAllToPersistenceQueues(const std::vector<RecheckBucketInfoCommand::SP>&);

    class CyclicBucketSpaceIterator {
    private:
        ContentBucketSpaceRepo::BucketSpaces _bucketSpaces;
        size_t _idx;
    public:
        using UP = std::unique_ptr<CyclicBucketSpaceIterator>;
        CyclicBucketSpaceIterator(const ContentBucketSpaceRepo::BucketSpaces &bucketSpaces);
        document::BucketSpace next() {
            return _bucketSpaces[(_idx++)%_bucketSpaces.size()];
        }
    };

    class BucketIdListResult {
    private:
        document::BucketSpace _bucketSpace;
        document::bucket::BucketIdList _buckets;
    public:
        BucketIdListResult();
        void reset(document::BucketSpace bucketSpace,
                   document::bucket::BucketIdList &buckets);
        const document::BucketSpace &bucketSpace() const { return _bucketSpace; }
        size_t size() const { return _buckets.size(); }
        bool empty() const { return _buckets.empty(); }
        const document::BucketId &back() const { return _buckets.back(); }
        void pop_back() { _buckets.pop_back(); }
    };

    spi::PersistenceProvider              & _provider;
    ServiceLayerComponent::UP               _component;
    framework::Thread::UP                   _thread;
    std::unique_ptr<config::ConfigFetcher>  _configFetcher;
    std::mutex                              _monitor;
    std::condition_variable                 _cond;
    std::mutex                              _stateLock;
    CyclicBucketSpaceIterator::UP           _bucketSpaces;
    BucketIdListResult                      _rechecksNotStarted;
    size_t                                  _pendingRequests;
    size_t                                  _maxPendingChunkSize;
    bool                                    _singleThreadMode; // For unit testing only
};

} // ns storage

