// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <tests/common/dummystoragelink.h>
#include <vespa/storageframework/defaultimplementation/clock/fakeclock.h>
#include <vespa/storage/common/hostreporter/hostinfo.h>
#include <vespa/storage/distributor/distributor.h>
#include <vespa/storage/frameworkimpl/component/distributorcomponentregisterimpl.h>
#include <vespa/storage/storageutil/utils.h>
#include <tests/common/teststorageapp.h>
#include <tests/distributor/messagesenderstub.h>
#include <vespa/storageapi/message/state.h>
#include <tests/common/testhelper.h>

namespace storage {

namespace distributor {

class DistributorTestUtil : private DoneInitializeHandler
{
public:
    DistributorTestUtil()
        : _messageSender(_sender, _senderDown)
    {
        _config = getStandardConfig(false);
    }
    virtual ~DistributorTestUtil() {};

    /**
     * Sets up the storage link chain.
     */
    void createLinks();
    void setTypeRepo(const document::DocumentTypeRepo::SP &repo);

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

    BucketDBUpdater& getBucketDBUpdater() {
        return _distributor->_bucketDBUpdater;
    }
    IdealStateManager& getIdealStateManager() {
        return _distributor->_idealStateManager;
    }
    ExternalOperationHandler& getExternalOperationHandler() {
        return _distributor->_externalOperationHandler;
    }

    Distributor& getDistributor() {
        return *_distributor;
    }

    bool tick() {
        framework::ThreadWaitInfo res(
                framework::ThreadWaitInfo::NO_MORE_CRITICAL_WORK_KNOWN);
        {
            framework::TickingLockGuard lock(
                    _distributor->_threadPool.freezeCriticalTicks());
            res.merge(_distributor->doCriticalTick(0));
        }
        res.merge(_distributor->doNonCriticalTick(0));
        return !res.waitWanted();
    }

    DistributorConfiguration& getConfig() {
        return const_cast<DistributorConfiguration&>(_distributor->getConfig());
    }

    vdstestlib::DirConfig& getDirConfig() {
        return _config;
    }

    BucketDatabase& getBucketDatabase() { return _distributor->getBucketDatabase(); }
    
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

    void setRedundancy(uint32_t redundancy);

    virtual void notifyDoneInitializing() {}

        // Must implement this for storage server interface for now
    virtual api::Timestamp getUniqueTimestamp() {
        return _component->getUniqueTimestamp();
    }

    void disableBucketActivationInConfig(bool disable);

    BucketDatabase::Entry getBucket(const document::BucketId& bId) const;

protected:
    vdstestlib::DirConfig _config;
    std::unique_ptr<TestDistributorApp> _node;
    framework::TickingThreadPool::UP _threadPool;
    std::unique_ptr<Distributor> _distributor;
    std::unique_ptr<storage::DistributorComponent> _component;
    MessageSenderStub _sender;
    MessageSenderStub _senderDown;
    HostInfo _hostInfo;

    struct MessageSenderImpl : public ChainedMessageSender {
        MessageSenderStub& _sender;
        MessageSenderStub& _senderDown;
        MessageSenderImpl(MessageSenderStub& up, MessageSenderStub& down)
            : _sender(up), _senderDown(down) {}

        void sendUp(const std::shared_ptr<api::StorageMessage>& msg) {
            _sender.send(msg);
        }
        void sendDown(const std::shared_ptr<api::StorageMessage>& msg) {
            _senderDown.send(msg);
        }
    };
    MessageSenderImpl _messageSender;
};

}

}

