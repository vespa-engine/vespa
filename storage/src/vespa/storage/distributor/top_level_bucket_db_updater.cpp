// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "top_level_bucket_db_updater.h"
#include "bucket_db_prune_elision.h"
#include "bucket_space_distribution_configs.h"
#include "bucket_space_distribution_context.h"
#include "top_level_distributor.h"
#include "distributor_bucket_space.h"
#include "distributormetricsset.h"
#include "node_supported_features_repo.h"
#include "simpleclusterinformation.h"
#include "stripe_access_guard.h"
#include <vespa/document/bucket/fixed_bucket_spaces.h>
#include <vespa/storage/common/global_bucket_space_distribution_converter.h>
#include <vespa/storageapi/message/persistence.h>
#include <vespa/storageapi/message/removelocation.h>
#include <vespa/vdslib/distribution/distribution.h>
#include <vespa/vdslib/state/clusterstate.h>
#include <vespa/vespalib/util/xmlstream.h>
#include <thread>

#include <vespa/log/bufferedlogger.h>
LOG_SETUP(".distributor.bucketdb.updater");

using storage::lib::Node;
using storage::lib::NodeType;
using document::BucketSpace;

namespace storage::distributor {

TopLevelBucketDBUpdater::TopLevelBucketDBUpdater(const DistributorNodeContext& node_ctx,
                                                 DistributorOperationContext& op_ctx,
                                                 DistributorInterface& distributor_interface,
                                                 ChainedMessageSender& chained_sender,
                                                 std::shared_ptr<const lib::Distribution> bootstrap_distribution,
                                                 StripeAccessor& stripe_accessor,
                                                 ClusterStateBundleActivationListener* state_activation_listener)
    : framework::StatusReporter("bucketdb", "Bucket DB Updater"),
      _stripe_accessor(stripe_accessor),
      _state_activation_listener(state_activation_listener),
      _active_state_bundle(lib::ClusterState()),
      _node_ctx(node_ctx),
      _op_ctx(op_ctx),
      _distributor_interface(distributor_interface),
      _pending_cluster_state(),
      _history(),
      _sender(distributor_interface),
      _chained_sender(chained_sender),
      _outdated_nodes_map(),
      _transition_timer(_node_ctx.clock()),
      _node_supported_features_repo(std::make_shared<const NodeSupportedFeaturesRepo>()),
      _stale_reads_enabled(false)
{
    // FIXME STRIPE top-level Distributor needs a proper way to track the current cluster state bundle!
    propagate_active_state_bundle_internally(true); // We're just starting up so assume ownership transfer.
    bootstrap_distribution_config(std::move(bootstrap_distribution));
}

TopLevelBucketDBUpdater::~TopLevelBucketDBUpdater() = default;

void
TopLevelBucketDBUpdater::propagate_active_state_bundle_internally(bool has_bucket_ownership_transfer) {
    for (auto& elem : _op_ctx.bucket_space_states()) {
        elem.second->set_cluster_state(_active_state_bundle.getDerivedClusterState(elem.first));
    }
    if (_state_activation_listener) {
        _state_activation_listener->on_cluster_state_bundle_activated(_active_state_bundle, has_bucket_ownership_transfer);
    }
}

void
TopLevelBucketDBUpdater::bootstrap_distribution_config(std::shared_ptr<const lib::Distribution> distribution) {
    auto global_distr = GlobalBucketSpaceDistributionConverter::convert_to_global(*distribution);
    _op_ctx.bucket_space_states().get(document::FixedBucketSpaces::default_space()).set_distribution(distribution);
    _op_ctx.bucket_space_states().get(document::FixedBucketSpaces::global_space()).set_distribution(global_distr);
    // TODO STRIPE do we need to bootstrap the stripes as well here? Or do they do this on their own volition?
    //   ... need to take a guard if so, so can probably not be done at ctor time..?
}

void
TopLevelBucketDBUpdater::propagate_distribution_config(const BucketSpaceDistributionConfigs& configs) {
    if (auto distr = configs.get_or_nullptr(document::FixedBucketSpaces::default_space())) {
        _op_ctx.bucket_space_states().get(document::FixedBucketSpaces::default_space()).set_distribution(distr);
    }
    if (auto distr = configs.get_or_nullptr(document::FixedBucketSpaces::global_space())) {
        _op_ctx.bucket_space_states().get(document::FixedBucketSpaces::global_space()).set_distribution(distr);
    }
}

void
TopLevelBucketDBUpdater::flush()
{
}

void
TopLevelBucketDBUpdater::print(std::ostream& out, bool verbose, const std::string& indent) const
{
    (void) verbose; (void) indent;
    out << "TopLevelBucketDBUpdater";
}

bool
TopLevelBucketDBUpdater::should_defer_state_enabling() const noexcept
{
    return stale_reads_enabled();
}

bool
TopLevelBucketDBUpdater::has_pending_cluster_state() const
{
    return static_cast<bool>(_pending_cluster_state);
}

void
TopLevelBucketDBUpdater::remove_superfluous_buckets(
        StripeAccessGuard& guard,
        const lib::ClusterStateBundle& new_state,
        bool is_distribution_config_change)
{
    const char* up_states = storage_node_up_states();
    for (auto& elem : _op_ctx.bucket_space_states()) {
        const auto& old_cluster_state(elem.second->get_cluster_state());
        const auto& new_cluster_state = new_state.getDerivedClusterState(elem.first);

        // Running a full DB sweep is expensive, so if the cluster state transition does
        // not actually indicate that buckets should possibly be removed, we elide it entirely.
        if (!is_distribution_config_change
            && db_pruning_may_be_elided(old_cluster_state, *new_cluster_state, up_states))
        {
            LOG(debug, "[bucket space '%s']: eliding DB pruning for state transition '%s' -> '%s'",
                document::FixedBucketSpaces::to_string(elem.first).data(),
                old_cluster_state.toString().c_str(), new_cluster_state->toString().c_str());
            continue;
        }
        auto maybe_lost = guard.remove_superfluous_buckets(elem.first, *new_cluster_state, is_distribution_config_change);
        if (maybe_lost.buckets != 0) {
            LOGBM(info, "After cluster state change %s, %zu buckets no longer "
                        "have available replicas. %zu documents in these buckets will "
                        "be unavailable until nodes come back up",
                  old_cluster_state.getTextualDifference(*new_cluster_state).c_str(),
                  maybe_lost.buckets, maybe_lost.documents);
        }
        maybe_inject_simulated_db_pruning_delay();
    }
}

namespace {

void maybe_sleep_for(std::chrono::milliseconds ms) {
    if (ms.count() > 0) {
        std::this_thread::sleep_for(ms);
    }
}

}

void
TopLevelBucketDBUpdater::maybe_inject_simulated_db_pruning_delay() {
    maybe_sleep_for(_op_ctx.distributor_config().simulated_db_pruning_latency());
}

void
TopLevelBucketDBUpdater::maybe_inject_simulated_db_merging_delay() {
    maybe_sleep_for(_op_ctx.distributor_config().simulated_db_merging_latency());
}

void
TopLevelBucketDBUpdater::ensure_transition_timer_started()
{
    // Don't overwrite start time if we're already processing a state, as
    // that will make transition times appear artificially low.
    if (!has_pending_cluster_state()) {
        _transition_timer = framework::MilliSecTimer(_node_ctx.clock());
    }
}

void
TopLevelBucketDBUpdater::complete_transition_timer()
{
    _distributor_interface.metrics()
            .stateTransitionTime.addValue(_transition_timer.getElapsedTimeAsDouble());
}

void
TopLevelBucketDBUpdater::storage_distribution_changed(const BucketSpaceDistributionConfigs& configs)
{
    propagate_distribution_config(configs);
    ensure_transition_timer_started();

    auto guard = _stripe_accessor.rendezvous_and_hold_all();
    // FIXME STRIPE might this cause a mismatch with the component stuff's own distribution config..?!
    guard->update_distribution_config(configs);
    remove_superfluous_buckets(*guard, _active_state_bundle, true);

    auto clusterInfo = std::make_shared<const SimpleClusterInformation>(
            _node_ctx.node_index(),
            _active_state_bundle,
            storage_node_up_states());
    _pending_cluster_state = PendingClusterState::createForDistributionChange(
            _node_ctx.clock(),
            std::move(clusterInfo),
            _sender,
            _op_ctx.bucket_space_states(),
            _op_ctx.generate_unique_timestamp());
    _outdated_nodes_map = _pending_cluster_state->getOutdatedNodesMap();

    guard->set_pending_cluster_state_bundle(_pending_cluster_state->getNewClusterStateBundle());
}

void
TopLevelBucketDBUpdater::reply_to_previous_pending_cluster_state_if_any()
{
    if (_pending_cluster_state.get() && _pending_cluster_state->hasCommand()) {
        _chained_sender.sendUp(
                std::make_shared<api::SetSystemStateReply>(*_pending_cluster_state->getCommand()));
    }
}

void
TopLevelBucketDBUpdater::reply_to_activation_with_actual_version(
        const api::ActivateClusterStateVersionCommand& cmd,
        uint32_t actualVersion)
{
    auto reply = std::make_shared<api::ActivateClusterStateVersionReply>(cmd);
    reply->setActualVersion(actualVersion);
    _chained_sender.sendUp(reply); // TODO let API accept rvalues
}

bool
TopLevelBucketDBUpdater::onSetSystemState(
        const std::shared_ptr<api::SetSystemStateCommand>& cmd)
{
    LOG(debug, "Received new cluster state %s",
        cmd->getSystemState().toString().c_str());

    const lib::ClusterStateBundle& state = cmd->getClusterStateBundle();

    if (state == _active_state_bundle) {
        return false;
    }
    ensure_transition_timer_started();
    // Separate timer since _transition_timer might span multiple pending states.
    framework::MilliSecTimer process_timer(_node_ctx.clock());

    auto guard = _stripe_accessor.rendezvous_and_hold_all();
    guard->update_read_snapshot_before_db_pruning();
    const auto& bundle = cmd->getClusterStateBundle();
    remove_superfluous_buckets(*guard, bundle, false);
    guard->update_read_snapshot_after_db_pruning(bundle);
    reply_to_previous_pending_cluster_state_if_any();

    auto clusterInfo = std::make_shared<const SimpleClusterInformation>(
                _node_ctx.node_index(),
                _active_state_bundle,
                storage_node_up_states());
    _pending_cluster_state = PendingClusterState::createForClusterStateChange(
            _node_ctx.clock(),
            std::move(clusterInfo),
            _sender,
            _op_ctx.bucket_space_states(),
            cmd,
            _outdated_nodes_map,
            _op_ctx.generate_unique_timestamp()); // FIXME STRIPE must be atomic across all threads
    _outdated_nodes_map = _pending_cluster_state->getOutdatedNodesMap();

    _distributor_interface.metrics().set_cluster_state_processing_time.addValue(
            process_timer.getElapsedTimeAsDouble());

    guard->set_pending_cluster_state_bundle(_pending_cluster_state->getNewClusterStateBundle());
    if (is_pending_cluster_state_completed()) {
        process_completed_pending_cluster_state(*guard);
    }
    return true;
}

bool
TopLevelBucketDBUpdater::onActivateClusterStateVersion(const std::shared_ptr<api::ActivateClusterStateVersionCommand>& cmd)
{
    if (has_pending_cluster_state() && _pending_cluster_state->isVersionedTransition()) {
        const auto pending_version = _pending_cluster_state->clusterStateVersion();
        if (pending_version == cmd->version()) {
            if (is_pending_cluster_state_completed()) {
                assert(_pending_cluster_state->isDeferred());
                auto guard = _stripe_accessor.rendezvous_and_hold_all();
                activate_pending_cluster_state(*guard);
            } else {
                LOG(error, "Received cluster state activation for pending version %u "
                           "without pending state being complete yet. This is not expected, "
                           "as no activation should be sent before all distributors have "
                           "reported that state processing is complete.", pending_version);
                reply_to_activation_with_actual_version(*cmd, 0);  // Invalid version, will cause re-send (hopefully when completed).
                return true;
            }
        } else {
            reply_to_activation_with_actual_version(*cmd, pending_version);
            return true;
        }
    } else if (should_defer_state_enabling()) {
        // Likely just a resend, but log warn for now to get a feel of how common it is.
        LOG(warning, "Received cluster state activation command for version %u, which "
                     "has no corresponding pending state. Likely resent operation.", cmd->version());
    } else {
        LOG(debug, "Received cluster state activation command for version %u, but distributor "
                   "config does not have deferred activation enabled. Treating as no-op.", cmd->version());
    }
    // Fall through to next link in call chain that cares about this message.
    return false;
}

bool
TopLevelBucketDBUpdater::onRequestBucketInfoReply(
        const std::shared_ptr<api::RequestBucketInfoReply>& repl)
{
    attempt_accept_reply_by_current_pending_state(repl);
    return true;
}

void
TopLevelBucketDBUpdater::attempt_accept_reply_by_current_pending_state(
        const std::shared_ptr<api::RequestBucketInfoReply>& repl)
{
    if (_pending_cluster_state.get()
        && _pending_cluster_state->onRequestBucketInfoReply(repl))
    {
        if (is_pending_cluster_state_completed()) {
            auto guard = _stripe_accessor.rendezvous_and_hold_all();
            process_completed_pending_cluster_state(*guard);
        }
    } else {
        // Reply is not recognized, so its corresponding command must have been
        // sent by a previous, preempted cluster state. We must still swallow the
        // reply to prevent it from being passed further down a storage chain that
        // does not expect it.
        LOG(spam, "Reply %s was not accepted by pending cluster state",
            repl->toString().c_str());
    }
}

void
TopLevelBucketDBUpdater::resend_delayed_messages()
{
    if (_pending_cluster_state) {
        _pending_cluster_state->resendDelayedMessages();
    }
}

bool
TopLevelBucketDBUpdater::is_pending_cluster_state_completed() const
{
    return _pending_cluster_state.get() && _pending_cluster_state->done();
}

void
TopLevelBucketDBUpdater::process_completed_pending_cluster_state(StripeAccessGuard& guard)
{
    if (_pending_cluster_state->isDeferred()) {
        LOG(debug, "Deferring completion of pending cluster state version %u until explicitly activated",
            _pending_cluster_state->clusterStateVersion());
        assert(_pending_cluster_state->hasCommand()); // Deferred transitions should only ever be created by state commands.
        // Sending down SetSystemState command will reach the state manager and a reply
        // will be auto-sent back to the cluster controller in charge. Once this happens,
        // it will send an explicit activation command once all distributors have reported
        // that their pending cluster states have completed.
        // A booting distributor will treat itself as "system Up" before the state has actually
        // taken effect via activation. External operation handler will keep operations from
        // actually being scheduled until state has been activated. The external operation handler
        // needs to be explicitly aware of the case where no state has yet to be activated.
        _chained_sender.sendDown(_pending_cluster_state->getCommand());
        _pending_cluster_state->clearCommand();
        return;
    }
    // Distribution config change or non-deferred cluster state. Immediately activate
    // the pending state without being told to do so explicitly.
    activate_pending_cluster_state(guard);
}

void
TopLevelBucketDBUpdater::activate_pending_cluster_state(StripeAccessGuard& guard)
{
    framework::MilliSecTimer process_timer(_node_ctx.clock());

    _pending_cluster_state->merge_into_bucket_databases(guard);
    maybe_inject_simulated_db_merging_delay();

    if (_pending_cluster_state->isVersionedTransition()) {
        LOG(debug, "Activating pending cluster state version %u", _pending_cluster_state->clusterStateVersion());
        enable_current_cluster_state_bundle_in_distributor_and_stripes(guard);
        if (_pending_cluster_state->hasCommand()) {
            _chained_sender.sendDown(_pending_cluster_state->getCommand());
        }
        add_current_state_to_cluster_state_history();
    } else {
        LOG(debug, "Activating pending distribution config");
        // TODO distribution changes cannot currently be deferred as they are not
        // initiated by the cluster controller!
        guard.notify_distribution_change_enabled();
    }

    _node_supported_features_repo = _node_supported_features_repo->make_union_of(
            _pending_cluster_state->gathered_node_supported_features());
    guard.update_node_supported_features_repo(_node_supported_features_repo);

    guard.update_read_snapshot_after_activation(_pending_cluster_state->getNewClusterStateBundle());
    _pending_cluster_state.reset();
    _outdated_nodes_map.clear();
    guard.clear_pending_cluster_state_bundle();
    complete_transition_timer();
    guard.clear_read_only_bucket_repo_databases();

    _distributor_interface.metrics().activate_cluster_state_processing_time.addValue(
            process_timer.getElapsedTimeAsDouble());
}

void
TopLevelBucketDBUpdater::enable_current_cluster_state_bundle_in_distributor_and_stripes(StripeAccessGuard& guard)
{
    const lib::ClusterStateBundle& state = _pending_cluster_state->getNewClusterStateBundle();

    _active_state_bundle = _pending_cluster_state->getNewClusterStateBundle();

    guard.enable_cluster_state_bundle(state, _pending_cluster_state->hasBucketOwnershipTransfer());
    propagate_active_state_bundle_internally(_pending_cluster_state->hasBucketOwnershipTransfer());

    LOG(debug, "TopLevelBucketDBUpdater finished processing state %s",
        state.getBaselineClusterState()->toString().c_str());
}

void TopLevelBucketDBUpdater::simulate_cluster_state_bundle_activation(const lib::ClusterStateBundle& activated_state,
                                                                       bool has_bucket_ownership_transfer)
{
    auto guard = _stripe_accessor.rendezvous_and_hold_all();
    guard->enable_cluster_state_bundle(activated_state, has_bucket_ownership_transfer);

    _active_state_bundle = activated_state;
    propagate_active_state_bundle_internally(has_bucket_ownership_transfer);
}

void
TopLevelBucketDBUpdater::add_current_state_to_cluster_state_history()
{
    _history.push_back(_pending_cluster_state->getSummary());

    if (_history.size() > 50) {
        _history.pop_front();
    }
}

vespalib::string
TopLevelBucketDBUpdater::getReportContentType(const framework::HttpUrlPath&) const
{
    return "text/xml";
}

namespace {

const vespalib::string ALL = "all";
const vespalib::string BUCKETDB = "bucketdb";
const vespalib::string BUCKETDB_UPDATER = "Bucket Database Updater";

}

bool
TopLevelBucketDBUpdater::reportStatus(std::ostream& out,
                                      const framework::HttpUrlPath& path) const
{
    using namespace vespalib::xml;
    XmlOutputStream xos(out);
    // FIXME(vekterli): have to do this manually since we cannot inherit
    // directly from XmlStatusReporter due to data races when TopLevelBucketDBUpdater
    // gets status requests directly.
    xos << XmlTag("status")
        << XmlAttribute("id", BUCKETDB)
        << XmlAttribute("name", BUCKETDB_UPDATER);
    report_xml_status(xos, path);
    xos << XmlEndTag();
    return true;
}

vespalib::string
TopLevelBucketDBUpdater::report_xml_status(vespalib::xml::XmlOutputStream& xos,
                                           const framework::HttpUrlPath&) const
{
    using namespace vespalib::xml;
    xos << XmlTag("bucketdb")
        << XmlTag("systemstate_active")
        << XmlContent(_active_state_bundle.getBaselineClusterState()->toString())
        << XmlEndTag();
    if (_pending_cluster_state) {
        xos << *_pending_cluster_state;
    }
    xos << XmlTag("systemstate_history");
    for (auto i(_history.rbegin()), e(_history.rend()); i != e; ++i) {
        xos << XmlTag("change")
            << XmlAttribute("from", i->_prevClusterState)
            << XmlAttribute("to", i->_newClusterState)
            << XmlAttribute("processingtime", i->_processingTime)
            << XmlEndTag();
    }
    xos << XmlEndTag()
        << XmlTag("single_bucket_requests");
    auto guard = _stripe_accessor.rendezvous_and_hold_all();
    guard->report_single_bucket_requests(xos);
    xos << XmlEndTag()
        << XmlTag("delayed_single_bucket_requests");
    guard->report_delayed_single_bucket_requests(xos);
    xos << XmlEndTag() << XmlEndTag();
    return "";
}

} // distributor
