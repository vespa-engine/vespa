// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/stllike/hash_set.h>

namespace storage::distributor {

/**
 * In the face of concurrent cluster state changes, cluster topology reconfigurations etc.,
 * it's possible for there to be pending mutating operations to nodes that the distributor
 * no longer should keep track of. Such operations must therefore be _cancelled_, either
 * fully or partially. A CancelScope represents the granularity at which an operation should
 * be cancelled.
 *
 * In the case of one or more nodes becoming unavailable, `fully_cancelled()` will be false
 * and `node_is_cancelled(x)` will return whether node `x` is explicitly cancelled.
 *
 * In the case of ownership transfers, `fully_cancelled()` will be true since the distributor
 * should no longer have any knowledge of the bucket. `node_is_cancelled(x)` is always
 * implicitly true for all values of `x` for full cancellations.
 */
class CancelScope {
public:
    using CancelledNodeSet = vespalib::hash_set<uint16_t>;
private:
    CancelledNodeSet _cancelled_nodes;
    bool             _fully_cancelled;

    struct fully_cancelled_ctor_tag {};

    explicit CancelScope(fully_cancelled_ctor_tag) noexcept;
    explicit CancelScope(CancelledNodeSet nodes) noexcept;
public:
    CancelScope();
    ~CancelScope();

    CancelScope(const CancelScope&);
    CancelScope& operator=(const CancelScope&);

    CancelScope(CancelScope&&) noexcept;
    CancelScope& operator=(CancelScope&&) noexcept;

    void add_cancelled_node(uint16_t node);
    void merge(const CancelScope& other);

    [[nodiscard]] bool fully_cancelled() const noexcept { return _fully_cancelled; }
    [[nodiscard]] bool is_cancelled() const noexcept {
        return (_fully_cancelled || !_cancelled_nodes.empty());
    }
    [[nodiscard]] bool node_is_cancelled(uint16_t node) const noexcept {
        return (fully_cancelled() || _cancelled_nodes.contains(node));
    }

    [[nodiscard]] const CancelledNodeSet& cancelled_nodes() const noexcept {
        return _cancelled_nodes;
    }

    static CancelScope of_fully_cancelled() noexcept;
    static CancelScope of_node_subset(CancelledNodeSet nodes) noexcept;
};

}
