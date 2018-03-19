// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "distributortestutil.h"
#include <vespa/storage/distributor/distributor.h>
#include <vespa/storage/distributor/distributor_bucket_space.h>
#include <vespa/config-stor-distribution.h>
#include <vespa/vespalib/text/stringtokenizer.h>
#include <vespa/document/test/make_document_bucket.h>
#include <vespa/document/test/make_bucket_space.h>

using document::test::makeBucketSpace;
using document::test::makeDocumentBucket;

namespace storage::distributor {

DistributorTestUtil::DistributorTestUtil()
    : _messageSender(_sender, _senderDown)
{
    _config = getStandardConfig(false);
}
DistributorTestUtil::~DistributorTestUtil() { }

void
DistributorTestUtil::createLinks()
{
    _node.reset(new TestDistributorApp(_config.getConfigId()));
    _threadPool = framework::TickingThreadPool::createDefault("distributor");
    _distributor.reset(new Distributor(
            _node->getComponentRegister(),
            *_threadPool,
            *this,
            true,
            _hostInfo,
            &_messageSender));
    _component.reset(new storage::DistributorComponent(_node->getComponentRegister(), "distrtestutil"));
};

void
DistributorTestUtil::setupDistributor(int redundancy,
                                      int nodeCount,
                                      const std::string& systemState,
                                      uint32_t earlyReturn,
                                      bool requirePrimaryToBeWritten)
{
    lib::Distribution::DistributionConfigBuilder config(
            lib::Distribution::getDefaultDistributionConfig(redundancy, nodeCount).get());
    config.redundancy = redundancy;
    config.initialRedundancy = earlyReturn;
    config.ensurePrimaryPersisted = requirePrimaryToBeWritten;
    auto distribution = std::make_shared<lib::Distribution>(config);
    _node->getComponentRegister().setDistribution(distribution);
    enableDistributorClusterState(systemState);
    // This is for all intents and purposes a hack to avoid having the
    // distributor treat setting the distribution explicitly as a signal that
    // it should send RequestBucketInfo to all configured nodes.
    // If we called storageDistributionChanged followed by enableDistribution
    // explicitly (which is what happens in "real life"), that is what would
    // take place.
    // The inverse case of this can be explicitly accomplished by calling
    // triggerDistributionChange().
    // This isn't pretty, folks, but it avoids breaking the world for now,
    // as many tests have implicit assumptions about this being the behavior.
    _distributor->propagateDefaultDistribution(distribution);
}

void
DistributorTestUtil::setRedundancy(uint32_t redundancy)
{
    auto distribution = std::make_shared<lib::Distribution>(
            lib::Distribution::getDefaultDistributionConfig(
                redundancy, 100));
    // Same rationale for not triggering a full distribution change as
    // in setupDistributor()
    _node->getComponentRegister().setDistribution(distribution);
    _distributor->propagateDefaultDistribution(std::move(distribution));
}

void
DistributorTestUtil::triggerDistributionChange(lib::Distribution::SP distr)
{
    _node->getComponentRegister().setDistribution(std::move(distr));
    _distributor->storageDistributionChanged();
    _distributor->enableNextDistribution();
}

void
DistributorTestUtil::setTypeRepo(const document::DocumentTypeRepo::SP &repo)
{
    _node->getComponentRegister().setDocumentTypeRepo(repo);
}

void
DistributorTestUtil::close()
{
    _component.reset(0);
    if (_distributor.get()) {
        _distributor->onClose();
    }
    _sender.clear();
    _node.reset(0);
    _config = getStandardConfig(false);
}

namespace {
    std::string dumpVector(const std::vector<uint16_t>& vec) {
        std::ostringstream ost;
        for (uint32_t i = 0; i < vec.size(); ++i) {
            if (i != 0) {
                ost << ",";
            }
            ost << vec[i];
        }
        return ost.str();
    }
}

std::string
DistributorTestUtil::getNodes(document::BucketId id)
{
    BucketDatabase::Entry entry = getBucket(id);

    if (!entry.valid()) {
        return id.toString();
    } else {
        std::vector<uint16_t> nodes = entry->getNodes();
        std::sort(nodes.begin(), nodes.end());

        std::ostringstream ost;
        ost << id << ": " << dumpVector(nodes);
        return ost.str();
    }
}

std::string
DistributorTestUtil::getIdealStr(document::BucketId id, const lib::ClusterState& state)
{
    if (!getExternalOperationHandler().ownsBucketInState(state, makeDocumentBucket(id))) {
        return id.toString();
    }

    std::vector<uint16_t> nodes;
    getDistribution().getIdealNodes(
            lib::NodeType::STORAGE, state, id, nodes);
    std::sort(nodes.begin(), nodes.end());
    std::ostringstream ost;
    ost << id << ": " << dumpVector(nodes);
    return ost.str();
}

void
DistributorTestUtil::addIdealNodes(const lib::ClusterState& state,
                                   const document::BucketId& id)
{
    BucketDatabase::Entry entry = getBucket(id);

    if (!entry.valid()) {
        entry = BucketDatabase::Entry(id);
    }

    std::vector<uint16_t> res;
    assert(_component.get());
    getDistribution().getIdealNodes(
            lib::NodeType::STORAGE, state, id, res);

    for (uint32_t i = 0; i < res.size(); ++i) {
        if (state.getNodeState(lib::Node(lib::NodeType::STORAGE, res[i])).getState() !=
            lib::State::MAINTENANCE)
        {
            entry->addNode(BucketCopy(0, res[i], api::BucketInfo(1,1,1)),
                           toVector<uint16_t>(0));
        }
    }

    getBucketDatabase().update(entry);
}

void DistributorTestUtil::addNodesToBucketDB(const document::Bucket& bucket, const std::string& nodeStr) {
    BucketDatabase::Entry entry = getBucket(bucket);

    if (!entry.valid()) {
        entry = BucketDatabase::Entry(bucket.getBucketId());
    }

    entry->clear();

    vespalib::StringTokenizer tokenizer(nodeStr, ",");
    for (uint32_t i = 0; i < tokenizer.size(); ++i) {
        vespalib::StringTokenizer tok2(tokenizer[i], "=");
        vespalib::StringTokenizer tok3(tok2[1], "/");

        api::BucketInfo info(atoi(tok3[0].c_str()),
                             atoi(tok3.size() > 1 ? tok3[1].c_str() : tok3[0].c_str()),
                             atoi(tok3.size() > 2 ? tok3[2].c_str() : tok3[0].c_str()));

        size_t flagsIdx = 3;

        // Meta info override? For simplicity, require both meta count and size
        if (tok3.size() > 4 && (!tok3[3].empty() && isdigit(tok3[3][0]))) {
            info.setMetaCount(atoi(tok3[3].c_str()));
            info.setUsedFileSize(atoi(tok3[4].c_str()));
            flagsIdx = 5;
        }

        if ((tok3.size() > flagsIdx + 1) && tok3[flagsIdx + 1] == "a") {
            info.setActive();
        } else {
            info.setActive(false);
        }
        if ((tok3.size() > flagsIdx + 2) && tok3[flagsIdx + 2] == "r") {
            info.setReady();
        } else {
            info.setReady(false);
        }

        uint16_t idx = atoi(tok2[0].c_str());
        BucketCopy node(
                0,
                idx,
                info);

        // Allow user to manually override trusted and active.
        if (tok3.size() > flagsIdx && tok3[flagsIdx] == "t") {
            node.setTrusted();
        }

        entry->addNodeManual(node);
    }

    getBucketDatabase(bucket.getBucketSpace()).update(entry);
}

void
DistributorTestUtil::addNodesToBucketDB(const document::BucketId& id,
                                        const std::string& nodeStr)
{
    addNodesToBucketDB(document::Bucket(makeBucketSpace(), id), nodeStr);
}

void
DistributorTestUtil::removeFromBucketDB(const document::BucketId& id)
{
    getBucketDatabase().remove(id);
}

void
DistributorTestUtil::addIdealNodes(const document::BucketId& id)
{
    addIdealNodes(*getExternalOperationHandler().getClusterStateBundle().getBaselineClusterState(), id);
}

void
DistributorTestUtil::insertBucketInfo(document::BucketId id,
                                      uint16_t node,
                                      uint32_t checksum,
                                      uint32_t count,
                                      uint32_t size,
                                      bool trusted,
                                      bool active)
{
    api::BucketInfo info(checksum, count, size);
    insertBucketInfo(id, node, info, trusted, active);
}

void
DistributorTestUtil::insertBucketInfo(document::BucketId id,
                                      uint16_t node,
                                      const api::BucketInfo& info,
                                      bool trusted,
                                      bool active)
{
    BucketDatabase::Entry entry = getBucketDatabase().get(id);
    if (!entry.valid()) {
        entry = BucketDatabase::Entry(id, BucketInfo());
    }

    api::BucketInfo info2(info);
    if (active) {
        info2.setActive();
    }
    BucketCopy copy(getExternalOperationHandler().getUniqueTimestamp(), node, info2);

    entry->addNode(copy.setTrusted(trusted), toVector<uint16_t>(0));

    getBucketDatabase().update(entry);
}

std::string
DistributorTestUtil::dumpBucket(const document::BucketId& bid)
{
    return getBucketDatabase().get(bid).toString();
}

void
DistributorTestUtil::sendReply(Operation& op,
                               int idx,
                               api::ReturnCode::Result result)
{
    if (idx == -1) {
        idx = _sender.commands.size() - 1;
    }
    assert(idx >= 0 && idx < static_cast<int>(_sender.commands.size()));

    std::shared_ptr<api::StorageCommand> cmd = _sender.commands[idx];
    api::StorageReply::SP reply(cmd->makeReply().release());
    reply->setResult(result);
    op.receive(_sender, reply);
}

BucketDatabase::Entry DistributorTestUtil::getBucket(const document::Bucket& bucket) const {
    return getBucketDatabase(bucket.getBucketSpace()).get(bucket.getBucketId());
}

BucketDatabase::Entry
DistributorTestUtil::getBucket(const document::BucketId& bId) const
{
    return getBucketDatabase().get(bId);
}

void
DistributorTestUtil::disableBucketActivationInConfig(bool disable)
{
    vespa::config::content::core::StorDistributormanagerConfigBuilder config;
    config.disableBucketActivation = disable;
    getConfig().configure(config);
}

BucketDBUpdater&
DistributorTestUtil::getBucketDBUpdater() {
    return _distributor->_bucketDBUpdater;
}
IdealStateManager&
DistributorTestUtil::getIdealStateManager() {
    return _distributor->_idealStateManager;
}
ExternalOperationHandler&
DistributorTestUtil::getExternalOperationHandler() {
    return _distributor->_externalOperationHandler;
}

bool
DistributorTestUtil::tick() {
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

DistributorConfiguration&
DistributorTestUtil::getConfig() {
    return const_cast<DistributorConfiguration&>(_distributor->getConfig());
}

DistributorBucketSpace &
DistributorTestUtil::getDistributorBucketSpace()
{
    return getBucketSpaceRepo().get(makeBucketSpace());
}

BucketDatabase&
DistributorTestUtil::getBucketDatabase() {
    return getDistributorBucketSpace().getBucketDatabase();
}

BucketDatabase& DistributorTestUtil::getBucketDatabase(document::BucketSpace space) {
    return getBucketSpaceRepo().get(space).getBucketDatabase();
}

const BucketDatabase&
DistributorTestUtil::getBucketDatabase() const {
    return getBucketSpaceRepo().get(makeBucketSpace()).getBucketDatabase();
}

const BucketDatabase& DistributorTestUtil::getBucketDatabase(document::BucketSpace space) const {
    return getBucketSpaceRepo().get(space).getBucketDatabase();
}

DistributorBucketSpaceRepo &
DistributorTestUtil::getBucketSpaceRepo() {
    return _distributor->getBucketSpaceRepo();
}

const DistributorBucketSpaceRepo &
DistributorTestUtil::getBucketSpaceRepo() const {
    return _distributor->getBucketSpaceRepo();
}

const lib::Distribution&
DistributorTestUtil::getDistribution() const {
    return getBucketSpaceRepo().get(makeBucketSpace()).getDistribution();
}

std::vector<document::BucketSpace>
DistributorTestUtil::getBucketSpaces() const
{
    std::vector<document::BucketSpace> res;
    for (const auto &repo : getBucketSpaceRepo()) {
        res.push_back(repo.first);
    }
    return res;
}

void
DistributorTestUtil::enableDistributorClusterState(vespalib::stringref state)
{
    _distributor->enableClusterStateBundle(lib::ClusterStateBundle(lib::ClusterState(state)));
}

}
