// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "top_level_distributor_test_util.h"
#include <vespa/config-stor-distribution.h>
#include <vespa/document/test/make_bucket_space.h>
#include <vespa/document/test/make_document_bucket.h>
#include <vespa/storage/distributor/top_level_distributor.h>
#include <vespa/storage/distributor/distributor_bucket_space.h>
#include <vespa/storage/distributor/distributor_stripe.h>
#include <vespa/storage/distributor/distributor_stripe_component.h>
#include <vespa/storage/distributor/distributor_stripe_pool.h>
#include <vespa/storage/distributor/distributor_stripe_thread.h>
#include <vespa/storage/distributor/distributor_total_metrics.h>
#include <vespa/storage/common/bucket_stripe_utils.h>
#include <vespa/vdslib/distribution/distribution.h>
#include <vespa/vespalib/text/stringtokenizer.h>

using document::test::makeBucketSpace;
using document::test::makeDocumentBucket;

namespace storage::distributor {

TopLevelDistributorTestUtil::TopLevelDistributorTestUtil()
    : _message_sender(_sender, _sender_down),
      _num_distributor_stripes(4)
{
    _config = getStandardConfig(false);
}

TopLevelDistributorTestUtil::~TopLevelDistributorTestUtil() = default;

void
TopLevelDistributorTestUtil::create_links()
{
    _node = std::make_unique<TestDistributorApp>(_config.getConfigId());
    _thread_pool = framework::TickingThreadPool::createDefault("distributor", 100ms);
    _stripe_pool = DistributorStripePool::make_non_threaded_pool_for_testing();
    _distributor.reset(new TopLevelDistributor(
            _node->getComponentRegister(),
            _node->node_identity(),
            *_thread_pool,
            *_stripe_pool,
            *this,
            _num_distributor_stripes,
            _host_info,
            &_message_sender));
    _component = std::make_unique<storage::DistributorComponent>(_node->getComponentRegister(), "distrtestutil");
};

void
TopLevelDistributorTestUtil::setup_distributor(int redundancy,
                                               int node_count,
                                               const std::string& cluster_state,
                                               uint32_t early_return,
                                               bool require_primary_to_be_written)
{
    setup_distributor(redundancy, node_count, lib::ClusterStateBundle(lib::ClusterState(cluster_state)),
                      early_return, require_primary_to_be_written);
}

void
TopLevelDistributorTestUtil::setup_distributor(int redundancy,
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
    _distributor->propagate_default_distribution_thread_unsafe(distribution);
    // Explicitly init the stripe pool since onOpen isn't called during testing
    _distributor->start_stripe_pool();
    enable_distributor_cluster_state(state);
}

size_t
TopLevelDistributorTestUtil::stripe_index_of_bucket(const document::BucketId& id) const noexcept
{
    return stripe_of_bucket_key(id.toKey(), _distributor->_n_stripe_bits);
}

size_t
TopLevelDistributorTestUtil::stripe_index_of_bucket(const document::Bucket& bucket) const noexcept
{
    return stripe_of_bucket_key(bucket.getBucketId().toKey(), _distributor->_n_stripe_bits);
}

void
TopLevelDistributorTestUtil::receive_set_system_state_command(const vespalib::string& state_str)
{
    auto state_cmd = std::make_shared<api::SetSystemStateCommand>(lib::ClusterState(state_str));
    handle_top_level_message(state_cmd); // TODO move semantics
}

bool
TopLevelDistributorTestUtil::handle_top_level_message(const std::shared_ptr<api::StorageMessage>& msg)
{
    return _distributor->onDown(msg);
}

void
TopLevelDistributorTestUtil::close()
{
    _component.reset();
    if (_distributor) {
        _stripe_pool->stop_and_join(); // Must be tagged as stopped prior to onClose
        _distributor->onClose();
    }
    _sender.clear();
    _node.reset();
    _config = getStandardConfig(false);
}

void
TopLevelDistributorTestUtil::add_nodes_to_stripe_bucket_db(const document::Bucket& bucket,
                                                           const std::string& nodeStr)
{
    BucketDatabase::Entry entry = get_bucket(bucket);

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
        BucketCopy node(0, idx, info);

        // Allow user to manually override trusted and active.
        if (tok3.size() > flagsIdx && tok3[flagsIdx] == "t") {
            node.setTrusted();
        }

        entry->addNodeManual(node);
    }

    stripe_bucket_database(stripe_index_of_bucket(bucket), bucket.getBucketSpace()).update(entry);
}

std::string
TopLevelDistributorTestUtil::get_ideal_str(document::BucketId id, const lib::ClusterState& state)
{
    if (!distributor_bucket_space(id).owns_bucket_in_state(state, id)) {
        return id.toString();
    }
    std::vector<uint16_t> nodes;
    _component->getDistribution()->getIdealNodes(lib::NodeType::STORAGE, state, id, nodes);
    std::sort(nodes.begin(), nodes.end());
    std::ostringstream ost;
    ost << id << ": " << dumpVector(nodes);
    return ost.str();
}

void
TopLevelDistributorTestUtil::add_ideal_nodes(const lib::ClusterState& state, const document::BucketId& id)
{
    BucketDatabase::Entry entry = get_bucket(id);

    if (!entry.valid()) {
        entry = BucketDatabase::Entry(id);
    }

    std::vector<uint16_t> res;
    assert(_component.get());
    _component->getDistribution()->getIdealNodes(lib::NodeType::STORAGE, state, id, res);

    for (uint32_t i = 0; i < res.size(); ++i) {
        if (state.getNodeState(lib::Node(lib::NodeType::STORAGE, res[i])).getState() !=
            lib::State::MAINTENANCE)
        {
            entry->addNode(BucketCopy(0, res[i], api::BucketInfo(1,1,1)),
                           toVector<uint16_t>(0));
        }
    }

    stripe_bucket_database(stripe_index_of_bucket(id)).update(entry);
}

void
TopLevelDistributorTestUtil::add_ideal_nodes(const document::BucketId& id)
{
    // TODO STRIPE good way of getting current active cluster state on top-level distributor
    // We assume that all stripes have the same cluster state internally, so just use the first.
    assert(_distributor->_stripes[0]);
    const auto& bundle = _distributor->_stripes[0]->getClusterStateBundle();
    add_ideal_nodes(*bundle.getBaselineClusterState(), id);
}

std::string
TopLevelDistributorTestUtil::get_nodes(document::BucketId id)
{
    BucketDatabase::Entry entry = get_bucket(id);

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

void
TopLevelDistributorTestUtil::add_nodes_to_stripe_bucket_db(const document::BucketId& id,
                                                           const std::string& nodeStr)
{
    add_nodes_to_stripe_bucket_db(document::Bucket(makeBucketSpace(), id), nodeStr);
}

BucketDatabase::Entry
TopLevelDistributorTestUtil::get_bucket(const document::Bucket& bucket) const
{
    return stripe_bucket_database(stripe_index_of_bucket(bucket), bucket.getBucketSpace()).get(bucket.getBucketId());
}

BucketDatabase::Entry
TopLevelDistributorTestUtil::get_bucket(const document::BucketId& bId) const
{
    return stripe_bucket_database(stripe_index_of_bucket(bId)).get(bId);
}

BucketSpaceStateMap&
TopLevelDistributorTestUtil::bucket_space_states() noexcept
{
    return _distributor->_component.bucket_space_states();
}

const BucketSpaceStateMap&
TopLevelDistributorTestUtil::bucket_space_states() const noexcept
{
    return _distributor->_component.bucket_space_states();
}

std::unique_ptr<StripeAccessGuard>
TopLevelDistributorTestUtil::acquire_stripe_guard()
{
    // Note: this won't actually interact with any threads, as the pool is running in single-threaded test mode.
    return _distributor->_stripe_accessor->rendezvous_and_hold_all();
}

TopLevelBucketDBUpdater&
TopLevelDistributorTestUtil::bucket_db_updater() {
    return *_distributor->_bucket_db_updater;
}

const IdealStateMetricSet&
TopLevelDistributorTestUtil::total_ideal_state_metrics() const
{
    assert(_distributor->_ideal_state_total_metrics);
    return *_distributor->_ideal_state_total_metrics;
}

const DistributorMetricSet&
TopLevelDistributorTestUtil::total_distributor_metrics() const
{
    assert(_distributor->_total_metrics);
    return *_distributor->_total_metrics;
}

DistributorBucketSpace&
TopLevelDistributorTestUtil::distributor_bucket_space(const document::BucketId& id)
{
    return stripe_of_bucket(id).getBucketSpaceRepo().get(makeBucketSpace());
}

const DistributorBucketSpace&
TopLevelDistributorTestUtil::distributor_bucket_space(const document::BucketId& id) const
{
    return stripe_of_bucket(id).getBucketSpaceRepo().get(makeBucketSpace());
}

DistributorStripe&
TopLevelDistributorTestUtil::stripe_of_bucket(const document::BucketId& id) noexcept
{
    return *_distributor->_stripes[stripe_index_of_bucket(id)];
}

const DistributorStripe&
TopLevelDistributorTestUtil::stripe_of_bucket(const document::BucketId& id) const noexcept
{
    return *_distributor->_stripes[stripe_index_of_bucket(id)];
}

DistributorStripe&
TopLevelDistributorTestUtil::stripe_of_bucket(const document::Bucket& bucket) noexcept
{
    return *_distributor->_stripes[stripe_index_of_bucket(bucket.getBucketId())];
}

const DistributorStripe&
TopLevelDistributorTestUtil::stripe_of_bucket(const document::Bucket& bucket) const noexcept
{
    return *_distributor->_stripes[stripe_index_of_bucket(bucket.getBucketId())];
}

bool
TopLevelDistributorTestUtil::tick(bool only_tick_top_level) {
    framework::ThreadWaitInfo res(
            framework::ThreadWaitInfo::NO_MORE_CRITICAL_WORK_KNOWN);
    {
        framework::TickingLockGuard lock(_distributor->_threadPool.freezeCriticalTicks());
        res.merge(_distributor->doCriticalTick(0));
    }
    res.merge(_distributor->doNonCriticalTick(0));
    bool did_work = !res.waitWanted();
    if (!only_tick_top_level) {
        for (auto& s : *_stripe_pool) {
            did_work |= s->stripe().tick();
        }
    }
    return did_work;
}

const DistributorConfig&
TopLevelDistributorTestUtil::current_distributor_config() const
{
    return _component->getDistributorConfig();
}

void
TopLevelDistributorTestUtil::reconfigure(const DistributorConfig& cfg)
{
    _node->getComponentRegister().setDistributorConfig(cfg);
    tick(); // Config is propagated upon next top-level tick
}

framework::MetricUpdateHook&
TopLevelDistributorTestUtil::distributor_metric_update_hook() {
    return _distributor->_metricUpdateHook;
}

BucketDatabase&
TopLevelDistributorTestUtil::stripe_bucket_database(uint16_t stripe_idx) {
    assert(stripe_idx < _distributor->_stripes.size());
    return _distributor->_stripes[stripe_idx]->getBucketSpaceRepo().get(makeBucketSpace()).getBucketDatabase();
}

BucketDatabase&
TopLevelDistributorTestUtil::stripe_bucket_database(uint16_t stripe_idx, document::BucketSpace space) {
    assert(stripe_idx < _distributor->_stripes.size());
    return _distributor->_stripes[stripe_idx]->getBucketSpaceRepo().get(space).getBucketDatabase();
}

const BucketDatabase&
TopLevelDistributorTestUtil::stripe_bucket_database(uint16_t stripe_idx) const {
    assert(stripe_idx < _distributor->_stripes.size());
    return _distributor->_stripes[stripe_idx]->getBucketSpaceRepo().get(makeBucketSpace()).getBucketDatabase();
}

const BucketDatabase&
TopLevelDistributorTestUtil::stripe_bucket_database(uint16_t stripe_idx, document::BucketSpace space) const {
    assert(stripe_idx < _distributor->_stripes.size());
    return _distributor->_stripes[stripe_idx]->getBucketSpaceRepo().get(space).getBucketDatabase();
}

// Hide how the sausages are made when directly accessing internal stripes
std::vector<DistributorStripe*>
TopLevelDistributorTestUtil::distributor_stripes() const {
    std::vector<DistributorStripe*> stripes;
    stripes.reserve(_distributor->_stripes.size());
    for (auto& s : _distributor->_stripes) {
        stripes.emplace_back(s.get());
    }
    return stripes;
}

bool
TopLevelDistributorTestUtil::all_distributor_stripes_are_in_recovery_mode() const {
    for (auto* s : distributor_stripes()) {
        if (!s->isInRecoveryMode()) {
            return false;
        }
    }
    return true;
}

void
TopLevelDistributorTestUtil::enable_distributor_cluster_state(vespalib::stringref state,
                                                              bool has_bucket_ownership_transfer)
{
    bucket_db_updater().simulate_cluster_state_bundle_activation(
            lib::ClusterStateBundle(lib::ClusterState(state)),
            has_bucket_ownership_transfer);
}

void
TopLevelDistributorTestUtil::enable_distributor_cluster_state(const lib::ClusterStateBundle& state)
{
    bucket_db_updater().simulate_cluster_state_bundle_activation(state);
}

std::vector<document::BucketSpace>
TopLevelDistributorTestUtil::bucket_spaces()
{
    return {document::FixedBucketSpaces::default_space(), document::FixedBucketSpaces::global_space()};
}

void
TopLevelDistributorTestUtil::trigger_distribution_change(std::shared_ptr<lib::Distribution> distr)
{
    _node->getComponentRegister().setDistribution(std::move(distr));
    _distributor->storageDistributionChanged();
    _distributor->enable_next_distribution_if_changed();
}

const lib::ClusterStateBundle&
TopLevelDistributorTestUtil::current_cluster_state_bundle() const
{
    // We assume that all stripes have the same cluster state internally, so just use the first.
    assert(_distributor->_stripes[0]);
    const auto& bundle = _distributor->_stripes[0]->getClusterStateBundle();
    // ... but sanity-check just to make sure...
    for (size_t i = 1; i < _num_distributor_stripes; ++i) {
        assert(_distributor->_stripes[i]->getClusterStateBundle() == bundle);
    }
    return bundle;
}

void
TopLevelDistributorTestUtil::tick_distributor_and_stripes_n_times(uint32_t n)
{
    for (uint32_t i = 0; i < n; ++i) {
        tick(false);
    }
}

void
TopLevelDistributorTestUtil::tick_top_level_distributor_n_times(uint32_t n)
{
    for (uint32_t i = 0; i < n; ++i) {
        tick(true);
    }
}

void
TopLevelDistributorTestUtil::complete_recovery_mode_on_all_stripes()
{
    for (auto* s : distributor_stripes()) {
        s->scanAllBuckets();
    }
}

}
