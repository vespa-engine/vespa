// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/util/small_vector.h>

namespace storage::distributor {

/*
 * Bundle of ideal service layer nodes for a bucket.
 */
class IdealServiceLayerNodesBundle {
public:
    using ConstNodesRef = vespalib::ConstArrayRef<uint16_t>;
    class Index {
    public:
        constexpr explicit Index(uint16_t index) noexcept : _index(index) {}
        constexpr bool valid() const noexcept {
            return _index < MAX_INDEX;
        }
        constexpr operator uint16_t () const noexcept { return _index; }
        static constexpr Index invalid() noexcept { return Index(MAX_INDEX); }
    private:
        static constexpr uint16_t MAX_INDEX = 0xffff;
        uint16_t _index;
    };
    struct Node2Index {
        virtual ~Node2Index() = default;
        virtual Index lookup(uint16_t node) const noexcept = 0;
    };
    class NonRetiredOrMaintenance2Index final : public Node2Index {
    public:
        NonRetiredOrMaintenance2Index(const IdealServiceLayerNodesBundle & idealState) noexcept : _idealState(idealState) {}
        Index lookup(uint16_t node) const noexcept override {
            return _idealState.nonretired_or_maintenance_index(node);
        }
    private:
        const IdealServiceLayerNodesBundle & _idealState;
    };
    class ConstNodesRef2Index final : public Node2Index {
    public:
        ConstNodesRef2Index(ConstNodesRef idealState) noexcept : _idealState(idealState) {}
        Index lookup(uint16_t node) const noexcept override;
    private:
        ConstNodesRef _idealState;
    };
    IdealServiceLayerNodesBundle() noexcept;
    IdealServiceLayerNodesBundle(IdealServiceLayerNodesBundle &&) noexcept;
    ~IdealServiceLayerNodesBundle();

    void set_nodes(ConstNodesRef nodes, ConstNodesRef nonretired_nodes, ConstNodesRef nonretired_or_maintenance_nodes);
    ConstNodesRef available_nodes() const noexcept { return {_nodes.data(), _available_sz}; }
    ConstNodesRef available_nonretired_nodes() const noexcept { return {_nodes.data() + _available_sz, _nonretired_sz}; }
    ConstNodesRef available_nonretired_or_maintenance_nodes() const noexcept {
        uint16_t offset = _available_sz + _nonretired_sz;
        return {_nodes.data() + offset, _nodes.size() - offset};
    }
    bool is_nonretired_or_maintenance(uint16_t node) const noexcept {
        return nonretired_or_maintenance_index(node) != Index::invalid();
    }
    NonRetiredOrMaintenance2Index nonretired_or_maintenance_to_index() const noexcept { return {*this}; }
    ConstNodesRef2Index available_to_index() const noexcept { return {available_nodes()}; }
private:
    struct LookupMap;
    Index nonretired_or_maintenance_index(uint16_t node) const noexcept;
    vespalib::SmallVector<uint16_t,16> _nodes;
    std::unique_ptr<LookupMap>         _nonretired_or_maintenance_node_2_index;
    uint16_t                           _available_sz;
    uint16_t                           _nonretired_sz;
};

}
