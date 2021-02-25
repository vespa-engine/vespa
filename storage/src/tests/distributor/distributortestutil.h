// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "distributor_message_sender_stub.h"
#include <tests/common/dummystoragelink.h>
#include <tests/common/testhelper.h>
#include <tests/common/teststorageapp.h>
#include <vespa/storage/common/hostreporter/hostinfo.h>
#include <vespa/storage/frameworkimpl/component/distributorcomponentregisterimpl.h>
#include <vespa/storage/storageutil/utils.h>
#include <vespa/storageapi/message/state.h>
#include <vespa/storageframework/defaultimplementation/clock/fakeclock.h>

namespace storage {

namespace framework { struct TickingThreadPool; }

namespace distributor {

class BucketDBUpdater;
class Distributor;
class DistributorBucketSpace;
class DistributorBucketSpaceRepo;
class DistributorComponent;
class IdealStateManager;
class ExternalOperationHandler;
class Operation;

class DistributorTestUtil : private DoneInitializeHandler
{
public:
    DistributorTestUtil();
    ~DistributorTestUtil();

    /**
     * Sets up the storage link chain.
     */
    void createLinks();
    void setTypeRepo(const std::shared_ptr<const document::DocumentTypeRepo> &repo);

    void close();

    /**
     * Returns a string with the nodes currently stored in the bucket
     * database for the given bucket.
     */
    std::string getNodes(document::BucketId id);

    /**
     * Returns a string with the ideal state nodes for the given bucket.
     */
    std::string getIdealStr(document::BucketId id, const lib::ClusterState& state);

    /**
     * Adds the ideal nodes for the given bucket and the given cluster state
     * to the bucket database.
     */
    void addIdealNodes(const lib::ClusterState& state, const document::BucketId& id);

    /**
     * Adds all the ideal nodes for the given bucket to the bucket database.
     */
    void addIdealNodes(const document::BucketId& id);

    /**
     * Parses the given string to a set of node => bucket info data,
     * and inserts them as nodes in the given bucket.
     * Format:
     *   "node1=checksum/docs/size,node2=checksum/docs/size"
     */
    void addNodesToBucketDB(const document::Bucket& bucket, const std::string& nodeStr);
    // As the above, but always inserts into default bucket space
    void addNodesToBucketDB(const document::BucketId& id, const std::string& nodeStr);

   /**
     * Removes the given bucket from the bucket database.
     */
    void removeFromBucketDB(const document::BucketId& id);

    /**
     * Inserts the given bucket information for the given bucket and node in
     * the bucket database.
     */
    void insertBucketInfo(document::BucketId id,
                          uint16_t node,
                          uint32_t checksum,
                          uint32_t count,
                          uint32_t size,
                          bool trusted = false,
                          bool active = false);

    /**
     * Inserts the given bucket information for the given bucket and node in
     * the bucket database.
     */
    void insertBucketInfo(document::BucketId id,
                          uint16_t node,
                          const api::BucketInfo& info,
                          bool trusted = false,
                          bool active = false);

    std::string dumpBucket(const document::BucketId& bucket);

    /**
     * Replies to message idx sent upwards with the given result code.
     * If idx = -1, replies to the last command received upwards.
     */
    void sendReply(Operation& op,
                   int idx = -1,
                   api::ReturnCode::Result result = api::ReturnCode::OK);

    BucketDBUpdater& getBucketDBUpdater();
    IdealStateManager& getIdealStateManager();
    ExternalOperationHandler& getExternalOperationHandler();
    storage::distributor::DistributorComponent& distributor_component();

    Distributor& getDistributor() {
        return *_distributor;
    }

    bool tick();

    DistributorConfiguration& getConfig();

    vdstestlib::DirConfig& getDirConfig() {
        return _config;
    }

    // TODO explicit notion of bucket spaces for tests
    DistributorBucketSpace &getDistributorBucketSpace();
    BucketDatabase& getBucketDatabase(); // Implicit default space only
    BucketDatabase& getBucketDatabase(document::BucketSpace space);
    const BucketDatabase& getBucketDatabase() const; // Implicit default space only
    const BucketDatabase& getBucketDatabase(document::BucketSpace space) const;
    DistributorBucketSpaceRepo &getBucketSpaceRepo();
    const DistributorBucketSpaceRepo &getBucketSpaceRepo() const;
    DistributorBucketSpaceRepo& getReadOnlyBucketSpaceRepo();
    const DistributorBucketSpaceRepo& getReadOnlyBucketSpaceRepo() const;

    const lib::Distribution& getDistribution() const;
    // "End to end" distribution change trigger, which will invoke the bucket
    // DB updater as expected based on the previous and new cluster state
    // and config.
    void triggerDistributionChange(std::shared_ptr<lib::Distribution> distr);
    
    framework::defaultimplementation::FakeClock& getClock() { return _node->getClock(); }
    DistributorComponentRegister& getComponentRegister() { return _node->getComponentRegister(); }
    DistributorComponentRegisterImpl& getComponentRegisterImpl() { return _node->getComponentRegister(); }

    StorageComponent& getComponent() {
        if (_component.get() == 0) {
            _component.reset(new storage::DistributorComponent(
                    _node->getComponentRegister(), "distributor_test_utils"));
        }
        return *_component;
    }

    void setupDistributor(int redundancy,
                          int nodeCount,
                          const std::string& systemState,
                          uint32_t earlyReturn = false,
                          bool requirePrimaryToBeWritten = true);

    void setup_distributor(int redundancy,
                           int node_count,
                           const lib::ClusterStateBundle& state,
                           uint32_t early_return = false,
                           bool require_primary_to_be_written = true);

    void setRedundancy(uint32_t redundancy);

    void notifyDoneInitializing() override {}

        // Must implement this for storage server interface for now
    virtual api::Timestamp getUniqueTimestamp() {
        return _component->getUniqueTimestamp();
    }

    void disableBucketActivationInConfig(bool disable);

    BucketDatabase::Entry getBucket(const document::Bucket& bucket) const;
    // Gets bucket entry from default space only
    BucketDatabase::Entry getBucket(const document::BucketId& bId) const;

    std::vector<document::BucketSpace> getBucketSpaces() const;

    DistributorMessageSenderStub& sender() noexcept { return _sender; }
    const DistributorMessageSenderStub& sender() const noexcept { return _sender; }
protected:
    vdstestlib::DirConfig _config;
    std::unique_ptr<TestDistributorApp> _node;
    std::unique_ptr<framework::TickingThreadPool> _threadPool;
    std::unique_ptr<Distributor> _distributor;
    std::unique_ptr<storage::DistributorComponent> _component;
    DistributorMessageSenderStub _sender;
    DistributorMessageSenderStub _senderDown;
    HostInfo _hostInfo;

    struct MessageSenderImpl : public ChainedMessageSender {
        DistributorMessageSenderStub& _sender;
        DistributorMessageSenderStub& _senderDown;
        MessageSenderImpl(DistributorMessageSenderStub& up, DistributorMessageSenderStub& down)
            : _sender(up), _senderDown(down) {}

        void sendUp(const std::shared_ptr<api::StorageMessage>& msg) override {
            _sender.send(msg);
        }
        void sendDown(const std::shared_ptr<api::StorageMessage>& msg) override {
            _senderDown.send(msg);
        }
    };
    MessageSenderImpl _messageSender;

    void enableDistributorClusterState(vespalib::stringref state);
    void enable_distributor_cluster_state(const lib::ClusterStateBundle& state);
};

}

}
