// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "distributortestutil.h"
#include <vespa/config-stor-distribution.h>
#include <vespa/document/test/make_bucket_space.h>
#include <vespa/document/test/make_document_bucket.h>
#include <vespa/storage/distributor/top_level_distributor.h>
#include <vespa/storage/distributor/distributor_bucket_space.h>
#include <vespa/storage/distributor/distributor_stripe.h>
#include <vespa/storage/distributor/distributor_stripe_component.h>
#include <vespa/storage/distributor/distributor_stripe_pool.h>
#include <vespa/vdslib/distribution/distribution.h>
#include <vespa/vespalib/text/stringtokenizer.h>

using document::test::makeBucketSpace;
using document::test::makeDocumentBucket;

namespace storage::distributor {

DistributorTestUtil::DistributorTestUtil()
    : _messageSender(_sender, _senderDown),
      _num_distributor_stripes(0) // TODO STRIPE change default
{
    _config = getStandardConfig(false);
}
DistributorTestUtil::~DistributorTestUtil() { }

void
DistributorTestUtil::createLinks()
{
    _node.reset(new TestDistributorApp(_config.getConfigId()));
    _threadPool = framework::TickingThreadPool::createDefault("distributor");
    _stripe_pool = std::make_unique<DistributorStripePool>();
    _distributor.reset(new TopLevelDistributor(
            _node->getComponentRegister(),
            _node->node_identity(),
            *_threadPool,
            *_stripe_pool,
            *this,
            _num_distributor_stripes,
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
    setup_distributor(redundancy, nodeCount, lib::ClusterStateBundle(lib::ClusterState(systemState)), earlyReturn, requirePrimaryToBeWritten);
}

void
DistributorTestUtil::setup_distributor(int redundancy,
                                       int node_count,
                                       const lib::ClusterStateBundle& state,
                                       uint32_t early_return,
                                       bool require_primary_to_be_written)
{
    lib::Distribution::DistributionConfigBuilder config(
            lib::Distribution::getDefaultDistributionConfig(redundancy, node_count).get());
    config.redundancy = redundancy;
    config.initialRedundancy = early_return;
    config.ensurePrimaryPersisted = require_primary_to_be_written;
    auto distribution = std::make_shared<lib::Distribution>(config);
    _node->getComponentRegister().setDistribution(distribution);
    enable_distributor_cluster_state(state);
    // This is for all intents and purposes a hack to avoid having the
    // distributor treat setting the distribution explicitly as a signal that
    // it should send RequestBucketInfo to all configured nodes.
    // If we called storage_distribution_changed followed by enableDistribution
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
DistributorTestUtil::receive_set_system_state_command(const vespalib::string& state_str)
{
    auto state_cmd = std::make_shared<api::SetSystemStateCommand>(lib::ClusterState(state_str));
    _distributor->handleMessage(state_cmd); // TODO move semantics
}

void
DistributorTestUtil::handle_top_level_message(const std::shared_ptr<api::StorageMessage>& msg)
{
    _distributor->handleMessage(msg);
}

void
DistributorTestUtil::setTypeRepo(const std::shared_ptr<const document::DocumentTypeRepo> &repo)
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
    if (!getDistributorBucketSpace().owns_bucket_in_state(state, id)) {
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

        api::BucketInfo info(atoi(tok3[0].data()),
                             atoi(tok3.size() > 1 ? tok3[1].data() : tok3[0].data()),
                             atoi(tok3.size() > 2 ? tok3[2].data() : tok3[0].data()));

        size_t flagsIdx = 3;

        // Meta info override? For simplicity, require both meta count and size
        if (tok3.size() > 4 && (!tok3[3].empty() && isdigit(tok3[3][0]))) {
            info.setMetaCount(atoi(tok3[3].data()));
            info.setUsedFileSize(atoi(tok3[4].data()));
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

        uint16_t idx = atoi(tok2[0].data());
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
    // TODO STRIPE roundabout way of getting state bundle..!
    addIdealNodes(*operation_context().cluster_state_bundle().getBaselineClusterState(), id);
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
    BucketCopy copy(operation_context().generate_unique_timestamp(), node, info2);

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
        idx = _sender.commands().size() - 1;
    }
    assert(idx >= 0 && idx < static_cast<int>(_sender.commands().size()));

    std::shared_ptr<api::StorageCommand> cmd = _sender.command(idx);
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

StripeBucketDBUpdater&
DistributorTestUtil::getBucketDBUpdater() {
    return _distributor->bucket_db_updater();
}
IdealStateManager&
DistributorTestUtil::getIdealStateManager() {
    return _distributor->ideal_state_manager();
}
ExternalOperationHandler&
DistributorTestUtil::getExternalOperationHandler() {
    return _distributor->external_operation_handler();
}

const storage::distributor::DistributorNodeContext&
DistributorTestUtil::node_context() const {
    return _distributor->distributor_component();
}

storage::distributor::DistributorStripeOperationContext&
DistributorTestUtil::operation_context() {
    return _distributor->distributor_component();
}

const DocumentSelectionParser&
DistributorTestUtil::doc_selection_parser() const {
    return _distributor->distributor_component();
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
    // TODO STRIPE avoid const cast
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

DistributorBucketSpaceRepo &
DistributorTestUtil::getReadOnlyBucketSpaceRepo() {
    return _distributor->getReadOnlyBucketSpaceRepo();
}

const DistributorBucketSpaceRepo &
DistributorTestUtil::getReadOnlyBucketSpaceRepo() const {
    return _distributor->getReadOnlyBucketSpaceRepo();
}

bool
DistributorTestUtil::distributor_is_in_recovery_mode() const noexcept {
    return _distributor->isInRecoveryMode();
}

const lib::ClusterStateBundle&
DistributorTestUtil::current_distributor_cluster_state_bundle() const noexcept {
    return getDistributor().getClusterStateBundle();
}

std::string
DistributorTestUtil::active_ideal_state_operations() const {
    return _distributor->getActiveIdealStateOperations();
}

const PendingMessageTracker&
DistributorTestUtil::pending_message_tracker() const noexcept {
    return _distributor->getPendingMessageTracker();
}

PendingMessageTracker&
DistributorTestUtil::pending_message_tracker() noexcept {
    return _distributor->getPendingMessageTracker();
}

std::chrono::steady_clock::duration
DistributorTestUtil::db_memory_sample_interval() const noexcept {
    return _distributor->db_memory_sample_interval();
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
    getBucketDBUpdater().simulate_cluster_state_bundle_activation(
            lib::ClusterStateBundle(lib::ClusterState(state)));
}

void
DistributorTestUtil::enable_distributor_cluster_state(const lib::ClusterStateBundle& state)
{
    getBucketDBUpdater().simulate_cluster_state_bundle_activation(state);
}

void
DistributorTestUtil::setSystemState(const lib::ClusterState& systemState) {
    _distributor->enableClusterStateBundle(lib::ClusterStateBundle(systemState));
}

}
