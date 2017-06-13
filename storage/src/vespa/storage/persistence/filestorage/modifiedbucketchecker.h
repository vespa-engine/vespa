// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/storage/common/storagecomponent.h>
#include <vespa/storage/common/servicelayercomponent.h>
#include <vespa/storage/common/storagelink.h>
#include <vespa/storage/config/config-stor-server.h>
#include <vespa/storage/persistence/messages.h>
#include <vespa/storage/persistence/types.h>
#include <vespa/document/bucket/bucketidlist.h>
#include <vespa/vespalib/util/sync.h>
#include <vespa/config/config.h>

namespace storage {

namespace spi { class PersistenceProvider; }

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
    bool requestModifiedBucketsFromProvider();
    void nextRecheckChunk(std::vector<RecheckBucketInfoCommand::SP>&);
    void dispatchAllToPersistenceQueues(const std::vector<RecheckBucketInfoCommand::SP>&);

    spi::PersistenceProvider& _provider;
    ServiceLayerComponent::UP _component;
    framework::Thread::UP _thread;
    config::ConfigFetcher _configFetcher;
    vespalib::Monitor _monitor;
    vespalib::Lock _stateLock;
    document::bucket::BucketIdList  _rechecksNotStarted;
    size_t _pendingRequests;
    size_t _maxPendingChunkSize;
    bool _singleThreadMode; // For unit testing only
};

} // ns storage

