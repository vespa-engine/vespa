// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

namespace storage::lib { class ClusterStateBundle; }

namespace storage::distributor {

/**
 * Listener where on_cluster_state_bundle_activated() is invoked by the top-level
 * bucket DB updater component upon a cluster state activation edge.
 *
 * Thread/concurrency note: this listener is always invoked from the top-level
 * distributor thread and in a context where all stripe threads are paused.
 * This means the callee must not directly or indirectly try to pause stripe
 * threads itself, but it may safely modify shared state since no stripe threads
 * are active.
 */
class ClusterStateBundleActivationListener {
public:
    virtual ~ClusterStateBundleActivationListener() = default;
    virtual void on_cluster_state_bundle_activated(const lib::ClusterStateBundle&,
                                                   bool has_bucket_ownership_transfer) = 0;
};

}
