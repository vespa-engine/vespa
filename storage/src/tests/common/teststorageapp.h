// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class storage::TestServiceLayerApp
 * \ingroup common
 *
 * \brief Helper class for tests involving service layer.
 *
 * Some components need some dependencies injected in order to work correctly.
 * This test class simplifies the process of creating these dependencies.
 *
 * Note that the interface between this class and the test class should be as
 * clean as possible, such that we can change as little as possible when
 * refactoring later. Also, advanced functionality should not be generated in
 * here, but rather fixed by tests themselves. Functionality here should be
 * needed by many tests, and we should avoid instantiating complex instances
 * here that several tests
 */
#pragma once

#include "testnodestateupdater.h"
#include <vespa/document/base/testdocman.h>
#include <vespa/document/bucket/fixed_bucket_spaces.h>
#include <persistence/spi/types.h>
#include <vespa/storage/bucketdb/storbucketdb.h>
#include <vespa/storage/common/doneinitializehandler.h>
#include <vespa/storage/common/hostreporter/hostinfo.h>
#include <vespa/storage/common/node_identity.h>
#include <vespa/storage/common/nodestateupdater.h>
#include <vespa/storage/frameworkimpl/component/distributorcomponentregisterimpl.h>
#include <vespa/storage/frameworkimpl/component/servicelayercomponentregisterimpl.h>
#include <vespa/storageframework/defaultimplementation/clock/realclock.h>
#include <vespa/storageframework/defaultimplementation/component/testcomponentregister.h>
#include <vespa/vespalib/util/sequencedtaskexecutor.h>
#include <atomic>

namespace storage {

namespace spi { struct PersistenceProvider; }
class StorageBucketDBInitializer;

DEFINE_PRIMITIVE_WRAPPER(uint16_t, NodeIndex);
DEFINE_PRIMITIVE_WRAPPER(uint16_t, NodeCount);
DEFINE_PRIMITIVE_WRAPPER(uint16_t, Redundancy);

class TestStorageApp
        : public framework::defaultimplementation::TestComponentRegister,
          public DoneInitializeHandler
{
    StorageComponentRegisterImpl& _compReg;

protected:
    document::TestDocMan _docMan;
    TestNodeStateUpdater _nodeStateUpdater;
    vespalib::string _configId;
    NodeIdentity _node_identity;
    std::atomic<bool> _initialized;

public:
    /**
     * Set up a storage application. If node index is not set, it will be
     * fetched from config if config id is given, otherwise it is set to 0.
     * If configId is given, some critical values are taken from config.
     * (node count, redundancy, node index etc). If configId set not set these
     * will just have some default values. A non-default node index will
     * override config, but be careful with this, as components may fetch index
     * from config themselves.
     */
    TestStorageApp(StorageComponentRegisterImpl::UP compReg,
                   const lib::NodeType&, NodeIndex = NodeIndex(0xffff),
                   vespalib::stringref configId = "");
    ~TestStorageApp();

    // Set functions, to be able to modify content while running.
    void setDistribution(Redundancy, NodeCount);
    void setTypeRepo(std::shared_ptr<const document::DocumentTypeRepo> repo);
    void setClusterState(const lib::ClusterState&);

    // Utility functions for getting a hold of currently used bits. Practical
    // to avoid adding extra components in the tests.
    StorageComponentRegisterImpl& getComponentRegister() { return _compReg; }
    document::TestDocMan& getTestDocMan() { return _docMan; }
    std::shared_ptr<const document::DocumentTypeRepo> getTypeRepo()
        { return _compReg.getTypeRepo(); }
    const document::BucketIdFactory& getBucketIdFactory()
        { return _compReg.getBucketIdFactory(); }
    TestNodeStateUpdater& getStateUpdater() { return _nodeStateUpdater; }
    std::shared_ptr<lib::Distribution> & getDistribution()
        { return _compReg.getDistribution(); }
    TestNodeStateUpdater& getNodeStateUpdater() { return _nodeStateUpdater; }
    uint16_t getIndex() const { return _compReg.getIndex(); }
    const NodeIdentity& node_identity() const noexcept { return _node_identity; }

    // The storage app also implements the done initializer interface, so it can
    // be sent to components needing this.
    DoneInitializeHandler& getDoneInitializeHandler() { return *this; }
    void notifyDoneInitializing() override { _initialized = true; }
    bool isInitialized() const { return _initialized; }
    void waitUntilInitialized(
            StorageBucketDBInitializer* initializer = 0,
            framework::SecondTime timeout = framework::SecondTime(30));

private:
    // Storage server interface implementation (until we can remove it)
    virtual api::Timestamp getUniqueTimestamp() { abort(); }
    [[nodiscard]] virtual StorBucketDatabase& content_bucket_db(document::BucketSpace) { abort(); }
    virtual StorBucketDatabase& getStorageBucketDatabase() { abort(); }
    virtual BucketDatabase& getBucketDatabase() { abort(); }
};

class TestServiceLayerApp : public TestStorageApp
{
    using PersistenceProviderUP = std::unique_ptr<spi::PersistenceProvider>;
    ServiceLayerComponentRegisterImpl& _compReg;
    PersistenceProviderUP _persistenceProvider;
    std::unique_ptr<vespalib::ISequencedTaskExecutor> _executor;
    HostInfo _host_info;

public:
    TestServiceLayerApp(vespalib::stringref configId);
    TestServiceLayerApp(NodeIndex = NodeIndex(0xffff), vespalib::stringref configId = "");
    ~TestServiceLayerApp();

    void setupDummyPersistence();
    void setPersistenceProvider(PersistenceProviderUP);

    ServiceLayerComponentRegisterImpl& getComponentRegister() { return _compReg; }
    HostInfo &get_host_info() noexcept { return _host_info; }

    spi::PersistenceProvider& getPersistenceProvider();

    StorBucketDatabase& content_bucket_db(document::BucketSpace space) override {
        return _compReg.getBucketSpaceRepo().get(space).bucketDatabase();
    }

    StorBucketDatabase& getStorageBucketDatabase() override {
        return _compReg.getBucketSpaceRepo().get(document::FixedBucketSpaces::default_space()).bucketDatabase();
    }
    vespalib::ISequencedTaskExecutor & executor() { return *_executor; }
};

class TestDistributorApp : public TestStorageApp,
                           public UniqueTimeCalculator
{
    DistributorComponentRegisterImpl& _compReg;
    std::mutex _accessLock;
    uint64_t _lastUniqueTimestampRequested;
    uint32_t _uniqueTimestampCounter;

    void configure(vespalib::stringref configId);

public:
    explicit TestDistributorApp(vespalib::stringref configId = "");
    explicit TestDistributorApp(NodeIndex index, vespalib::stringref configId = "");
    ~TestDistributorApp() override;

    DistributorComponentRegisterImpl& getComponentRegister() {
        return _compReg;
    }

    api::Timestamp getUniqueTimestamp() override;
};

} // storageo
