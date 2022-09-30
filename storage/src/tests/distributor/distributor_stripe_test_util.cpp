// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "distributor_stripe_test_util.h"
#include <vespa/config-stor-distribution.h>
#include <vespa/document/test/make_bucket_space.h>
#include <vespa/document/test/make_document_bucket.h>
#include <vespa/storage/distributor/distributor_bucket_space.h>
#include <vespa/storage/distributor/distributor_stripe.h>
#include <vespa/storage/distributor/distributor_stripe_component.h>
#include <vespa/storage/distributor/distributormetricsset.h>
#include <vespa/storage/distributor/ideal_state_total_metrics.h>
#include <vespa/storage/distributor/node_supported_features_repo.h>
#include <vespa/vdslib/distribution/distribution.h>
#include <vespa/vespalib/text/stringtokenizer.h>
#include <vespa/vespalib/stllike/hash_map.hpp>

using document::test::makeBucketSpace;
using document::test::makeDocumentBucket;

namespace storage::distributor {

DistributorStripeTestUtil::DistributorStripeTestUtil()
    : _config(),
      _node(),
      _stripe(),
      _sender(),
      _senderDown(),
      _hostInfo(),
      _done_initializing(true),
      _messageSender(_sender, _senderDown)
{
    _config = getStandardConfig(false);
}

DistributorStripeTestUtil::~DistributorStripeTestUtil() = default;

void
DistributorStripeTestUtil::createLinks()
{
    _node = std::make_unique<TestDistributorApp>(_config.getConfigId());
    _metrics = std::make_shared<DistributorMetricSet>();
    _ideal_state_metrics = std::make_shared<IdealStateMetricSet>();
    _stripe = std::make_unique<DistributorStripe>(_node->getComponentRegister(),
                                                  *_metrics,
                                                  *_ideal_state_metrics,
                                                  _node->node_identity(),
                                                  _messageSender,
                                                  *this,
                                                  _done_initializing);
}

void
DistributorStripeTestUtil::setup_stripe(int redundancy,
                                        int nodeCount,
                                        const std::string& systemState,
                                        uint32_t earlyReturn,
                                        bool requirePrimaryToBeWritten)
{
    setup_stripe(redundancy, nodeCount, lib::ClusterStateBundle(lib::ClusterState(systemState)), earlyReturn, requirePrimaryToBeWritten);
}

void
DistributorStripeTestUtil::setup_stripe(int redundancy,
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
    enable_cluster_state(state);

    // TODO STRIPE: Update this comment now that stripe is used instead.
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
    auto new_configs = BucketSpaceDistributionConfigs::from_default_distribution(std::move(distribution));
    _stripe->update_distribution_config(new_configs);
}

void
DistributorStripeTestUtil::set_redundancy(uint32_t redundancy)
{
    auto distribution = std::make_shared<lib::Distribution>(
            lib::Distribution::getDefaultDistributionConfig(redundancy, 100));
    // Same rationale for not triggering a full distribution change as
    // in setup_stripe() above
    _node->getComponentRegister().setDistribution(distribution);
    _stripe->propagateDefaultDistribution(std::move(distribution));
}

void
DistributorStripeTestUtil::trigger_distribution_change(lib::Distribution::SP distr)
{
    _node->getComponentRegister().setDistribution(distr);
    auto new_config = BucketSpaceDistributionConfigs::from_default_distribution(distr);
    _stripe->update_distribution_config(new_config);
}

std::shared_ptr<DistributorConfiguration>
DistributorStripeTestUtil::make_config() const
{
    return std::make_shared<DistributorConfiguration>(_stripe->_component);
}

void
DistributorStripeTestUtil::configure_stripe(std::shared_ptr<const DistributorConfiguration> config)
{
    _stripe->update_total_distributor_config(config);
}

void
DistributorStripeTestUtil::configure_stripe(const ConfigBuilder& builder)
{
    auto config = make_config();
    config->configure(builder);
    configure_stripe(config);
}

void
DistributorStripeTestUtil::receive_set_system_state_command(const vespalib::string& state_str)
{
    auto state_cmd = std::make_shared<api::SetSystemStateCommand>(lib::ClusterState(state_str));
    _stripe->handleMessage(state_cmd); // TODO move semantics
}

void
DistributorStripeTestUtil::handle_top_level_message(const std::shared_ptr<api::StorageMessage>& msg)
{
    _stripe->handleMessage(msg);
}

void
DistributorStripeTestUtil::simulate_set_pending_cluster_state(const vespalib::string& state_str)
{
    lib::ClusterState state(state_str);
    lib::ClusterStateBundle pending_state(state);
    for (auto& space : _stripe->getBucketSpaceRepo()) {
        const auto& new_cluster_state = pending_state.getDerivedClusterState(space.first);
        _stripe->update_read_snapshot_before_db_pruning();
        _stripe->remove_superfluous_buckets(space.first, *new_cluster_state, false);
        _stripe->update_read_snapshot_after_db_pruning(pending_state);
    }
    _stripe->set_pending_cluster_state_bundle(pending_state);
}

void
DistributorStripeTestUtil::clear_pending_cluster_state_bundle()
{
    _stripe->clear_pending_cluster_state_bundle();
}

void
DistributorStripeTestUtil::setTypeRepo(const std::shared_ptr<const document::DocumentTypeRepo>& repo)
{
    _node->getComponentRegister().setDocumentTypeRepo(repo);
}

void
DistributorStripeTestUtil::close()
{
    _stripe->flush_and_close();
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
DistributorStripeTestUtil::getNodes(document::BucketId id)
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
DistributorStripeTestUtil::getIdealStr(document::BucketId id, const lib::ClusterState& state)
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
DistributorStripeTestUtil::addIdealNodes(const lib::ClusterState& state,
                                         const document::BucketId& id)
{
    BucketDatabase::Entry entry = getBucket(id);

    if (!entry.valid()) {
        entry = BucketDatabase::Entry(id);
    }

    std::vector<uint16_t> res;
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

void
DistributorStripeTestUtil::addNodesToBucketDB(const document::Bucket& bucket, const std::string& nodeStr)
{
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
DistributorStripeTestUtil::addNodesToBucketDB(const document::BucketId& id,
                                              const std::string& nodeStr)
{
    addNodesToBucketDB(document::Bucket(makeBucketSpace(), id), nodeStr);
}

void
DistributorStripeTestUtil::removeFromBucketDB(const document::BucketId& id)
{
    getBucketDatabase().remove(id);
}

void
DistributorStripeTestUtil::addIdealNodes(const document::BucketId& id)
{
    // TODO STRIPE roundabout way of getting state bundle..!
    addIdealNodes(*operation_context().cluster_state_bundle().getBaselineClusterState(), id);
}

void
DistributorStripeTestUtil::insertBucketInfo(document::BucketId id,
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
DistributorStripeTestUtil::insertBucketInfo(document::BucketId id,
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
DistributorStripeTestUtil::dumpBucket(const document::BucketId& bid)
{
    return getBucketDatabase().get(bid).toString();
}

void
DistributorStripeTestUtil::sendReply(Operation& op,
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

BucketDatabase::Entry
DistributorStripeTestUtil::getBucket(const document::Bucket& bucket) const
{
    return getBucketDatabase(bucket.getBucketSpace()).get(bucket.getBucketId());
}

BucketDatabase::Entry
DistributorStripeTestUtil::getBucket(const document::BucketId& bId) const
{
    return getBucketDatabase().get(bId);
}

void
DistributorStripeTestUtil::disableBucketActivationInConfig(bool disable)
{
    ConfigBuilder builder;
    builder.disableBucketActivation = disable;
    configure_stripe(builder);
}

StripeBucketDBUpdater&
DistributorStripeTestUtil::getBucketDBUpdater() {
    return _stripe->bucket_db_updater();
}

IdealStateManager&
DistributorStripeTestUtil::getIdealStateManager() {
    return _stripe->ideal_state_manager();
}

ExternalOperationHandler&
DistributorStripeTestUtil::getExternalOperationHandler() {
    return _stripe->external_operation_handler();
}

const storage::distributor::DistributorNodeContext&
DistributorStripeTestUtil::node_context() const {
    return _stripe->_component;
}

storage::distributor::DistributorStripeOperationContext&
DistributorStripeTestUtil::operation_context() {
    return _stripe->_component;
}

const DocumentSelectionParser&
DistributorStripeTestUtil::doc_selection_parser() const {
    return _stripe->_component;
}

DistributorMetricSet&
DistributorStripeTestUtil::metrics()
{
    return *_metrics;
}

bool
DistributorStripeTestUtil::tick()
{
    return _stripe->tick();
}

const DistributorConfiguration&
DistributorStripeTestUtil::getConfig() {
    return _stripe->getConfig();
}

DistributorBucketSpace&
DistributorStripeTestUtil::getDistributorBucketSpace() {
    return getBucketSpaceRepo().get(makeBucketSpace());
}

BucketDatabase&
DistributorStripeTestUtil::getBucketDatabase() {
    return getDistributorBucketSpace().getBucketDatabase();
}

BucketDatabase&
DistributorStripeTestUtil::getBucketDatabase(document::BucketSpace space) {
    return getBucketSpaceRepo().get(space).getBucketDatabase();
}

const BucketDatabase&
DistributorStripeTestUtil::getBucketDatabase() const {
    return getBucketSpaceRepo().get(makeBucketSpace()).getBucketDatabase();
}

const BucketDatabase&
DistributorStripeTestUtil::getBucketDatabase(document::BucketSpace space) const {
    return getBucketSpaceRepo().get(space).getBucketDatabase();
}

DistributorBucketSpaceRepo&
DistributorStripeTestUtil::getBucketSpaceRepo() {
    return _stripe->getBucketSpaceRepo();
}

const DistributorBucketSpaceRepo&
DistributorStripeTestUtil::getBucketSpaceRepo() const {
    return _stripe->getBucketSpaceRepo();
}

DistributorBucketSpaceRepo&
DistributorStripeTestUtil::getReadOnlyBucketSpaceRepo() {
    return _stripe->getReadOnlyBucketSpaceRepo();
}

const DistributorBucketSpaceRepo&
DistributorStripeTestUtil::getReadOnlyBucketSpaceRepo() const {
    return _stripe->getReadOnlyBucketSpaceRepo();
}

bool
DistributorStripeTestUtil::stripe_is_in_recovery_mode() const noexcept {
    return _stripe->isInRecoveryMode();
}

const lib::ClusterStateBundle&
DistributorStripeTestUtil::current_cluster_state_bundle() const noexcept {
    return _stripe->getClusterStateBundle();
}

std::string
DistributorStripeTestUtil::active_ideal_state_operations() const {
    return _stripe->getActiveIdealStateOperations();
}

const PendingMessageTracker&
DistributorStripeTestUtil::pending_message_tracker() const noexcept {
    return _stripe->getPendingMessageTracker();
}

PendingMessageTracker&
DistributorStripeTestUtil::pending_message_tracker() noexcept {
    return _stripe->getPendingMessageTracker();
}

std::chrono::steady_clock::duration
DistributorStripeTestUtil::db_memory_sample_interval() const noexcept {
    return _stripe->db_memory_sample_interval();
}

void
DistributorStripeTestUtil::set_node_supported_features(uint16_t node, const NodeSupportedFeatures& features) {
    vespalib::hash_map<uint16_t, NodeSupportedFeatures> new_features;
    new_features[node] = features;
    _stripe->update_node_supported_features_repo(_stripe->node_supported_features_repo().make_union_of(new_features));
}

const lib::Distribution&
DistributorStripeTestUtil::getDistribution() const {
    return getBucketSpaceRepo().get(makeBucketSpace()).getDistribution();
}

std::vector<document::BucketSpace>
DistributorStripeTestUtil::getBucketSpaces() const
{
    std::vector<document::BucketSpace> res;
    for (const auto &repo : getBucketSpaceRepo()) {
        res.push_back(repo.first);
    }
    return res;
}

void
DistributorStripeTestUtil::enable_cluster_state(vespalib::stringref state)
{
    getBucketDBUpdater().simulate_cluster_state_bundle_activation(
            lib::ClusterStateBundle(lib::ClusterState(state)));
}

void
DistributorStripeTestUtil::enable_cluster_state(const lib::ClusterStateBundle& state)
{
    getBucketDBUpdater().simulate_cluster_state_bundle_activation(state);
}

void
DistributorStripeTestUtil::setSystemState(const lib::ClusterState& systemState) {
    _stripe->enableClusterStateBundle(lib::ClusterStateBundle(systemState));
}

}
