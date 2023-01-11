// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "hnsw_graph.h"
#include "hnsw_index.h"
#include <vespa/vespalib/util/rcuvector.hpp>
#include <vespa/vespalib/datastore/array_store.hpp>

namespace search::tensor {

template <HnswIndexType type>
HnswGraph<type>::HnswGraph()
  : nodes(),
    nodes_size(1u),
    levels_store(HnswIndex<type>::make_default_level_array_store_config(), {}),
    links_store(HnswIndex<type>::make_default_link_array_store_config(), {}),
    entry_nodeid_and_level()
{
    nodes.ensure_size(1, NodeType());
    EntryNode entry;
    set_entry_node(entry);
}

template <HnswIndexType type>
HnswGraph<type>::~HnswGraph() = default;

template <HnswIndexType type>
typename HnswGraph<type>::LevelsRef
HnswGraph<type>::make_node(uint32_t nodeid, uint32_t docid, uint32_t subspace, uint32_t num_levels)
{
    nodes.ensure_size(nodeid + 1, NodeType());
    // A document cannot be added twice.
    assert(!get_levels_ref(nodeid).valid());
    // Note: The level array instance lives as long as the document is present in the index.
    std::vector<AtomicEntryRef> levels(num_levels, AtomicEntryRef());
    auto levels_ref = levels_store.add(levels);
    auto& node = nodes[nodeid];
    node.levels_ref().store_release(levels_ref);
    node.store_docid(docid);
    node.store_subspace(subspace);
    if (nodeid >= nodes_size.load(std::memory_order_relaxed)) {
        nodes_size.store(nodeid + 1, std::memory_order_release);
    }
    return levels_ref;
}

template <HnswIndexType type>
void
HnswGraph<type>::remove_node(uint32_t nodeid)
{
    auto levels_ref = get_levels_ref(nodeid);
    assert(levels_ref.valid());
    auto levels = levels_store.get(levels_ref);
    vespalib::datastore::EntryRef invalid;
    nodes[nodeid].levels_ref().store_release(invalid);
    // Ensure data referenced through the old ref can be recycled:
    levels_store.remove(levels_ref);
    for (size_t i = 0; i < levels.size(); ++i) {
        auto old_links_ref = levels[i].load_relaxed();
        links_store.remove(old_links_ref);
    }
    if (nodeid + 1 == nodes_size.load(std::memory_order_relaxed)) {
        trim_nodes_size();
    }
}

template <HnswIndexType type>
void
HnswGraph<type>::trim_nodes_size()
{
    uint32_t check_nodeid = nodes_size.load(std::memory_order_relaxed) - 1;
    while (check_nodeid > 0u && !get_levels_ref(check_nodeid).valid()) {
        --check_nodeid;
    }
    nodes_size.store(check_nodeid + 1, std::memory_order_release);
}

template <HnswIndexType type>
void     
HnswGraph<type>::set_link_array(uint32_t nodeid, uint32_t level, const LinkArrayRef& new_links)
{
    auto new_links_ref = links_store.add(new_links);
    auto levels_ref = get_levels_ref(nodeid);
    assert(levels_ref.valid());
    auto levels = levels_store.get_writable(levels_ref);
    assert(level < levels.size());
    auto old_links_ref = levels[level].load_relaxed();
    levels[level].store_release(new_links_ref);
    links_store.remove(old_links_ref);
}

template <HnswIndexType type>
typename HnswGraph<type>::Histograms
HnswGraph<type>::histograms() const
{
    Histograms result;
    size_t num_nodes = nodes_size.load(std::memory_order_acquire);
    for (size_t i = 0; i < num_nodes; ++i) {
        auto levels_ref = acquire_levels_ref(i);
        if (levels_ref.valid()) {
            uint32_t levels = 0;
            uint32_t l0links = 0;
            auto level_array = levels_store.get(levels_ref);
            levels = level_array.size();
            if (levels > 0) {
                auto links_ref = level_array[0].load_acquire();
                auto link_array = links_store.get(links_ref);
                l0links = link_array.size();
            }
            while (result.level_histogram.size() <= levels) {
                result.level_histogram.push_back(0);
            }
            ++result.level_histogram[levels];
            while (result.links_histogram.size() <= l0links) {
                result.links_histogram.push_back(0);
            }
            ++result.links_histogram[l0links];
        }
    }
    return result;
}

template <HnswIndexType type>
void
HnswGraph<type>::set_entry_node(EntryNode node) {
    uint64_t value = node.level;
    value <<= 32;
    value |= node.nodeid;
    if (node.levels_ref.valid()) {
        assert(node.level >= 0);
        assert(node.nodeid > 0);
    } else {
        assert(node.level == -1);
        assert(node.nodeid == 0);
    }
    entry_nodeid_and_level.store(value, std::memory_order_release);
}

template struct HnswGraph<HnswIndexType::SINGLE>;
template struct HnswGraph<HnswIndexType::MULTI>;

} // namespace

namespace vespalib {

template class RcuVectorBase<search::tensor::HnswSimpleNode>;
template class RcuVector<search::tensor::HnswSimpleNode>;
template class RcuVectorBase<search::tensor::HnswNode>;
template class RcuVector<search::tensor::HnswNode>;

}
